/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/


package org.locationtech.geomesa.accumulo.iterators

import java.util.Date

import com.vividsolutions.jts.geom.Envelope
import org.geotools.data.Query
import org.geotools.filter.text.ecql.ECQL
import org.geotools.filter.visitor.ExtractBoundsFilterVisitor
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.joda.time.{DateTime, DateTimeZone}
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.TestWithDataStore
import org.locationtech.geomesa.accumulo.index.QueryHints
import org.locationtech.geomesa.features.ScalaSimpleFeature
import org.locationtech.geomesa.utils.geotools.Conversions._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.util.Random

@RunWith(classOf[JUnitRunner])
class Z3DensityIteratorTest extends Specification with TestWithDataStore {

  sequential

  // to ensure the z3 index is used, the geom must be a point and the queries must include geom + time
  override val spec = "id:java.lang.Integer,attr:java.lang.Double,dtg:Date,geom:Point:srid=4326"

  def getDensity(query: String): List[(Double, Double, Double)] = {
    val q = new Query(sftName, ECQL.toFilter(query))
    val geom = q.getFilter.accept(ExtractBoundsFilterVisitor.BOUNDS_VISITOR, null).asInstanceOf[Envelope]
    q.getHints.put(QueryHints.DENSITY_BBOX_KEY, new ReferencedEnvelope(geom, DefaultGeographicCRS.WGS84))
    q.getHints.put(QueryHints.WIDTH_KEY, 500)
    q.getHints.put(QueryHints.HEIGHT_KEY, 500)
    val decode = Z3DensityIterator.decodeResult(geom, 500, 500)
    fs.getFeatures(q).features().flatMap(decode).toList
  }

  "Z3DensityIterator" should {

    "reduce total features returned" in {
      clearFeatures()
      val features = (0 until 150).toArray.map { i =>
        val sf = new ScalaSimpleFeature(i.toString, sft)
        sf.setAttribute(0, i.toString)
        sf.setAttribute(1, "1.0")
        sf.setAttribute(2, new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate)
        sf.setAttribute(3, "POINT(-77 38)")
        sf
      }
      addFeatures(features)

      val q = "(dtg between '2012-01-01T18:00:00.000Z' AND '2012-01-01T23:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)"
      val density = getDensity(q)
      density.length must beLessThan(150)
    }

    "maintain total weight of points" in {
      clearFeatures()
      val features = (0 until 150).toArray.map { i =>
        val sf = new ScalaSimpleFeature(i.toString, sft)
        sf.setAttribute(0, i.toString)
        sf.setAttribute(1, "1.0")
        sf.setAttribute(2, new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate)
        sf.setAttribute(3, "POINT(-77 38)")
        sf
      }
      addFeatures(features)

      val q = "(dtg between '2012-01-01T18:00:00.000Z' AND '2012-01-01T23:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)"
      val density = getDensity(q)
      density.map(_._3).sum mustEqual 150
    }

    "maintain weights irrespective of dates" in {
      clearFeatures()
      val date = new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate.getTime
      val features = (0 until 150).toArray.map { i =>
        val sf = new ScalaSimpleFeature(i.toString, sft)
        sf.setAttribute(0, i.toString)
        sf.setAttribute(1, "1.0")
        sf.setAttribute(2, new Date(date + i * 60000))
        sf.setAttribute(3, "POINT(-77 38)")
        sf
      }
      addFeatures(features)

      val q = "(dtg between '2012-01-01T18:00:00.000Z' AND '2012-01-01T23:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)"
      val density = getDensity(q)
      density.map(_._3).sum mustEqual 150
    }

    "correctly bin points" in {
      clearFeatures()
      val date = new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate.getTime
      val features = (0 until 150).toArray.map { i =>
        // space out the points very slightly around 5 primary latitudes 1 degree apart
        val lat = (i / 30) + 1 + (Random.nextDouble() - 0.5) / 1000.0
        val sf = new ScalaSimpleFeature(i.toString, sft)
        sf.setAttribute(0, i.toString)
        sf.setAttribute(1, "1.0")
        sf.setAttribute(2, new Date(date + i * 60000))
        sf.setAttribute(3, s"POINT($lat 37)")
        sf
      }
      addFeatures(features)

      val q = "(dtg between '2012-01-01T18:00:00.000Z' AND '2012-01-01T23:00:00.000Z') and BBOX(geom, -1, 33, 6, 40)"
      val density = getDensity(q)
      density.map(_._3).sum mustEqual 150

      val compiled = density.groupBy(d => (d._1, d._2)).map { case (pt, group) => group.map(_._3).sum }

      // should be 5 bins of 30
      compiled must haveLength(5)
      forall(compiled)(_ mustEqual 30)
    }
  }
}
