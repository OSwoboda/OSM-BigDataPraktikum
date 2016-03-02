/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.process

import java.util.Date

import org.geotools.data.DataUtilities
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.feature.simple.{SimpleFeatureBuilder, SimpleFeatureTypeBuilder}
import org.geotools.geometry.jts.{JTS, JTSFactoryFinder}
import org.geotools.process.factory.{DescribeParameter, DescribeProcess, DescribeResult}
import org.geotools.process.vector.VectorProcess
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.joda.time.DateTime
import org.joda.time.DateTime.Property
import org.locationtech.geomesa.utils.geotools.SftBuilder
import org.opengis.feature.simple.SimpleFeature

@DescribeProcess(title = "Point2PointProcess", description = "Aggregates a collection of points into a collection of line segments")
class Point2PointProcess extends VectorProcess {

  private val baseType = new SftBuilder().lineString("geom", default = true).build("point2point")

  private val gf = JTSFactoryFinder.getGeometryFactory

  @DescribeResult(name = "result", description = "Aggregated feature collection")
  def execute(

               @DescribeParameter(name = "data", description = "Input feature collection")
               data: SimpleFeatureCollection,

               @DescribeParameter(name = "groupingField", description = "Field on which to group")
               groupingField: String,

               @DescribeParameter(name = "sortField", description = "Field on which to sort (must be Date type)")
               sortField: String,

               @DescribeParameter(name = "minimumNumberOfPoints", description = "Minimum number of points")
               minPoints: Int,

               @DescribeParameter(name = "breakOnDay", description = "Break connections on day marks")
               breakOnDay: Boolean,

               @DescribeParameter(name = "filterSingularPoints", description = "Filter out segments that fall on the same point", defaultValue = "true")
               filterSingularPoints: Boolean

               ): SimpleFeatureCollection = {

    import org.locationtech.geomesa.utils.geotools.Conversions._

    val queryType = data.getSchema
    val sftBuilder = new SimpleFeatureTypeBuilder()
    sftBuilder.init(baseType)
    val groupingFieldIndex = data.getSchema.indexOf(groupingField)
    sftBuilder.add(queryType.getAttributeDescriptors.get(groupingFieldIndex))
    val sortFieldIndex = data.getSchema.indexOf(sortField)
    val sortDesc = queryType.getAttributeDescriptors.get(sortFieldIndex)
    val sortAttrName = sortDesc.getLocalName
    val sortType = sortDesc.getType.getBinding
    sftBuilder.add(s"${sortAttrName}_start", sortType)
    sftBuilder.add(s"${sortAttrName}_end", sortType)

    val sft = sftBuilder.buildFeatureType()
    val builder = new SimpleFeatureBuilder(sft)

    val lineFeatures =
      data.features().toList
        .groupBy(_.get(groupingFieldIndex).asInstanceOf[String])
        .filter { case (_, coll) => coll.size > minPoints }
        .flatMap { case (group, coll) =>

        val globalSorted = coll.sortBy(_.get[java.util.Date](sortFieldIndex))

        val groups =
          if (!breakOnDay) Array(globalSorted)
          else
            globalSorted
              .groupBy { f => getDayOfYear(sortFieldIndex, f) }
              .filter { case (_, g) => g.size >= 2 }  // need at least two points in a day to create a
              .map { case (_, g) => g }.toArray

        val results = groups.flatMap { sorted =>
          sorted.sliding(2).zipWithIndex.map { case (ptLst, idx) =>
            import org.locationtech.geomesa.utils.geotools.Conversions.RichSimpleFeature
            val pts = ptLst.map(_.point.getCoordinate)
            val length = JTS.orthodromicDistance(pts.head, pts.last, DefaultGeographicCRS.WGS84)

            val group    = ptLst.head.getAttribute(groupingFieldIndex)
            val startDtg = ptLst.head.getAttribute(sortAttrName)
            val endDtg   = ptLst.last.getAttribute(sortAttrName)
            val attrs    = Array[AnyRef](gf.createLineString(pts.toArray), group, startDtg, endDtg)
            val sf = builder.buildFeature(s"$group-$idx", attrs)
            (length, sf)
          }
        }

        if (filterSingularPoints) results.filter { case (length, _) => length > 0.0 }.map { case (_, sf) => sf }
        else results.map { case (_, sf) => sf }
      }

    DataUtilities.collection(lineFeatures.toArray)
  }

  def getDayOfYear(sortFieldIndex: Int, f: SimpleFeature): Property =
    new DateTime(f.getAttribute(sortFieldIndex).asInstanceOf[Date]).dayOfYear()
}
