/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.index

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import org.apache.accumulo.core.data.{Key, Mutation}
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.joda.time.{DateTime, DateTimeZone}
import org.locationtech.geomesa.accumulo.data.AccumuloFeatureWriter.FeatureToWrite
import org.locationtech.geomesa.utils.geohash.GeoHash
import org.locationtech.geomesa.utils.geotools.Conversions.RichSimpleFeature
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType
import org.opengis.feature.simple.SimpleFeatureType

import scala.collection.JavaConversions._

case class STIndexEncoder(sft: SimpleFeatureType, rowf: TextFormatter, cff: TextFormatter, cqf: TextFormatter)
    extends LazyLogging {

  import org.locationtech.geomesa.utils.geohash.GeohashUtils._

  val formats = Array(rowf, cff, cqf)
  val dtgFieldIndex = sft.getDtgIndex

  val mutations: (Seq[GeoHash], FeatureToWrite, DateTime, Boolean) => Seq[Mutation] =
    if (IndexSchema.mayContainDuplicates(sft)) polyMutations else pointMutations

  // the resolutions are valid for decomposed objects are all 5-bit boundaries
  // between 5-bits and 35-bits (inclusive)
  lazy val decomposableResolutions: ResolutionRange = new ResolutionRange(0, 35, 5)

  // the maximum number of sub-units into which a geometry may be decomposed
  lazy val maximumDecompositions: Int = 5

  def encode(toWrite: FeatureToWrite, delete: Boolean = false): Seq[Mutation] = {

    logger.trace(s"encoding feature: $toWrite")

    // decompose non-point geometries into multiple index entries
    // (a point will return a single GeoHash at the maximum allowable resolution)
    val geohashes =
      decomposeGeometry(toWrite.feature.geometry, maximumDecompositions, decomposableResolutions)

    logger.trace(s"decomposed ${toWrite.feature.geometry} into geohashes:" +
        s" ${geohashes.map(_.hash).mkString(",")})}")

    val dt = dtgFieldIndex.map(toWrite.feature.get[Date])
        .map(new DateTime(_))
        .getOrElse(new DateTime())
        .withZone(DateTimeZone.UTC)

    mutations(geohashes, toWrite, dt, delete)
  }

  // no duplicates - we know that each key will be on a different row
  private def pointMutations(geohashes: Seq[GeoHash],
                             toWrite: FeatureToWrite,
                             dt: DateTime,
                             delete: Boolean): Seq[Mutation] =
    Seq((true, toWrite.indexValue), (false, toWrite.dataValue)).flatMap { case (index, value) =>
      geohashes.map { gh =>
        formats.map(_.format(gh, dt, toWrite.feature, index)) match { case Array(row, cf, cq) =>
          val m = new Mutation(row)
          if (delete) {
            m.putDelete(cf, cq, toWrite.columnVisibility)
          } else {
            m.put(cf, cq, toWrite.columnVisibility, value)
          }
          m
        }
      }
    }

  // group mutations by row
  private def polyMutations(geohashes: Seq[GeoHash],
                            toWrite: FeatureToWrite,
                            dt: DateTime,
                            delete: Boolean): Seq[Mutation] = {
    val keys = Seq((true, toWrite.indexValue), (false, toWrite.dataValue)).flatMap { case (index, value) =>
      geohashes.map { gh =>
        formats.map(_.format(gh, dt, toWrite.feature, index)) match {
          case Array(row, cf, cq) => (row, cf, cq, value)
        }
      }
    }
    keys.groupBy(_._1).map { case (row, keys) =>
      val m = new Mutation(row)
      if (delete) {
        keys.foreach { case (_, cf, cq, _) => m.putDelete(cf, cq, toWrite.columnVisibility) }
      } else {
        keys.foreach { case (_, cf, cq, value) => m.put(cf, cq, toWrite.columnVisibility, value) }
      }
      m
    }.toSeq
  }
}

object IndexEntryDecoder {
  val localBuilder = new ThreadLocal[SimpleFeatureBuilder] {
    override def initialValue(): SimpleFeatureBuilder = new SimpleFeatureBuilder(indexSFT)
  }
}

import org.locationtech.geomesa.accumulo.index.IndexEntryDecoder._

case class IndexEntryDecoder(ghDecoder: GeohashDecoder, dtDecoder: Option[DateDecoder]) {
  def decode(key: Key) = {
    val builder = localBuilder.get
    builder.reset()
    builder.addAll(List(ghDecoder.decode(key).geom, dtDecoder.map(_.decode(key))))
    builder.buildFeature("")
  }
}
