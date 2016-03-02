/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.tools.commands

import java.io._

import com.beust.jcommander.{JCommander, Parameter, Parameters}
import com.typesafe.scalalogging.LazyLogging
import org.geotools.data.Query
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.geomesa.accumulo.data.AccumuloFeatureStore
import org.locationtech.geomesa.accumulo.stats.StatWriter
import org.locationtech.geomesa.tools.Utils.Formats
import org.locationtech.geomesa.tools.Utils.Formats._
import org.locationtech.geomesa.tools._
import org.locationtech.geomesa.tools.commands.ExportCommand.ExportParameters
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType
import org.opengis.filter.Filter

import scala.util.{Failure, Success, Try}

class ExportCommand(parent: JCommander) extends CommandWithCatalog(parent) with LazyLogging {
  override val command = "export"
  override val params = new ExportParameters

  override def execute() = {

    val fmt = params.format.toLowerCase
    val features = getFeatureCollection(fmt)
    val exporter: FeatureExporter = fmt match {
      case CSV | TSV       => DelimitedExport(getWriter(), params)
      case SHP             => new ShapefileExport(getFile())
      case GeoJson | JSON  => new GeoJsonExport(getWriter())
      case GML             => new GmlExport(getOutputStream())
      case BIN             => BinFileExport(getOutputStream(), params)
      case _ =>
        throw new IllegalArgumentException(s"Unsupported export format. Supported formats are: ${Formats.All.mkString(",")}.")
    }
    try {
      exporter.write(features)
      logger.info("Feature export complete to " + (if (usefile) params.file.getPath else "standard out"))
    } finally {
      exporter.flush()
      exporter.close()
    }

    // force StatsWriter to write stats - otherwise we get a zookeeper exception we can't catch
    StatWriter.flush()
  }

  def getFeatureCollection(fmt: String): SimpleFeatureCollection = {
    val sft = ds.getSchema(params.featureName)
    fmt match {
      case SHP =>
        val schemaString =
          if (params.attributes == null) {
            ShapefileExport.modifySchema(sft)
          } else {
            ShapefileExport.replaceGeomInAttributesString(params.attributes, sft)
          }
        getFeatureCollection(Some(schemaString))
      case BIN =>
        sft.getDtgField.foreach(BinFileExport.DEFAULT_TIME = _)
        getFeatureCollection(Some(BinFileExport.getAttributeList(params)))
      case _ => getFeatureCollection()
    }
  }

  def getFeatureCollection(overrideAttributes: Option[String] = None): SimpleFeatureCollection = {
    val filter = Option(params.cqlFilter).map(ECQL.toFilter).getOrElse(Filter.INCLUDE)
    logger.debug(s"Applying CQL filter ${filter.toString}")
    val q = new Query(params.featureName, filter)
    Option(params.maxFeatures).foreach(q.setMaxFeatures(_))

    // If there are override attributes given as an arg or via command line params
    // split attributes by "," meanwhile allowing to escape it by "\,".
    overrideAttributes.orElse(Option(params.attributes)).foreach { attributes =>
      val splitAttrs = attributes.split("""(?<!\\),""").map(_.trim.replace("\\,", ","))
      logger.debug("Attributes used for query transform: " + splitAttrs.mkString("|"))
      q.setPropertyNames(splitAttrs)
    }

    // get the feature store used to query the GeoMesa data
    val fs = ds.getFeatureSource(params.featureName).asInstanceOf[AccumuloFeatureStore]

    // and execute the query
    Try(fs.getFeatures(q)) match {
      case Success(fc) => fc
      case Failure(ex) =>
        throw new Exception("Error: Could not create a SimpleFeatureCollection to export. Please ensure " +
        "that all arguments are correct in the previous command.", ex)
    }
  }

  def getOutputStream(): OutputStream = {
    val out = Option(params.file) match {
      case Some(file) => new FileOutputStream(file)
      case None       => System.out
    }
    new BufferedOutputStream(out)
  }
  
  def getWriter(): Writer = new OutputStreamWriter(getOutputStream())
  
  def getFile(): File = Option(params.file) match {
    case Some(file) => file
    case None       => throw new Exception("Error: -o or --output for file-based output is required for shapefile export (stdout not supported for shape files)")
  }

  def usefile = Option(params.file).nonEmpty
}

object ExportCommand {
  @Parameters(commandDescription = "Export a GeoMesa feature")
  class ExportParameters extends OptionalCqlFilterParameters {
    @Parameter(names = Array("-F", "--format"), description = "Format to export (csv|tsv|gml|json|shp|bin)")
    var format: String = "csv"

    @Parameter(names = Array("-m", "--max-features"), description = "Maximum number of features to return. default: Long.MaxValue")
    var maxFeatures: Integer = Int.MaxValue

    @Parameter(names = Array("-a", "--attributes"), description = "Attributes from feature to export " +
      "(comma-separated)...Comma-separated expressions with each in the format " +
      "attribute[=filter_function_expression]|derived-attribute=filter_function_expression. " +
      "filter_function_expression is an expression of filter function applied to attributes, literals " +
      "and filter functions, i.e. can be nested")
    var attributes: String = null

    @Parameter(names = Array("--id-attribute"), description = "name of the id attribute to export")
    var idAttribute: String = null

    @Parameter(names = Array("--lat-attribute"), description = "name of the latitude attribute to export")
    var latAttribute: String = null

    @Parameter(names = Array("--lon-attribute"), description = "name of the longitude attribute to export")
    var lonAttribute: String = null

    @Parameter(names = Array("--dt-attribute"), description = "name of the date attribute to export")
    var dateAttribute: String = null

    @Parameter(names = Array("--label-attribute"), description = "name of the attribute to use as a bin file label")
    var labelAttribute: String = null

    @Parameter(names = Array("-o", "--output"), description = "name of the file to output to instead of std out")
    var file: File = null
  }
}
