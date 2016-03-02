/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/


package org.locationtech.geomesa.filter.function

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat

import org.geotools.data.collection.ListFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.junit.runner.RunWith
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.locationtech.geomesa.utils.text.WKTUtils
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BinaryOutputEncoderTest extends Specification {

  "BinaryViewerOutputFormat" should {

    val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    "encode a point feature collection" in {
      val sft = SimpleFeatureTypes.createType("bintest",
        "track:String,label:Long,lat:Double,lon:Double,dtg:Date,geom:Point:srid=4326")
      val baseDtg = dateFormat.parse("2014-01-01 08:09:00").getTime

      val fc = new ListFeatureCollection(sft)
      val builder = new SimpleFeatureBuilder(sft)
      (0 until 4).foreach { i =>
        val point = WKTUtils.read(s"POINT (45 5$i)")
        val date = dateFormat.parse(s"2014-01-01 08:0${9-i}:00")
        builder.addAll(Array(s"1234-$i", java.lang.Long.valueOf(i), 45 + i, 50, date, point).asInstanceOf[Array[AnyRef]])
        fc.add(builder.buildFeature(s"$i"))
      }

      "with label field" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dtg", Some("track"), Some("label"), None, AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 24, (i + 1) * 24))
          decoded.dtg mustEqual baseDtg - 60 * 1000 * i
          decoded.lat mustEqual 45
          decoded.lon mustEqual 50 + i
          decoded.trackId mustEqual s"1234-$i".hashCode.toString
          decoded.asInstanceOf[ExtendedValues].label mustEqual java.lang.Long.valueOf(i)
        }
        success
      }

      "without label field" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dtg", Some("track"), None, None, AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 16, (i + 1) * 16))
          decoded.dtg mustEqual baseDtg - 60 * 1000 * i
          decoded.lat mustEqual 45
          decoded.lon mustEqual 50 + i
          decoded.trackId mustEqual s"1234-$i".hashCode.toString
          decoded must beAnInstanceOf[BasicValues]
        }
        success
      }

      "with id field" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dtg", Some("id"), None, None, AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 16, (i + 1) * 16))
          decoded.dtg mustEqual baseDtg - 60 * 1000 * i
          decoded.lat mustEqual 45
          decoded.lon mustEqual 50 + i
          decoded.trackId mustEqual s"$i".hashCode.toString
          decoded must beAnInstanceOf[BasicValues]
        }
        success
      }

      "without custom lat/lon" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dtg", Some("track"), None, Some(("lat", "lon")), AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 16, (i + 1) * 16))
          decoded.dtg mustEqual baseDtg - 60 * 1000 * i
          decoded.lat mustEqual 45 + i
          decoded.lon mustEqual 50
          decoded.trackId mustEqual s"1234-$i".hashCode.toString
          decoded must beAnInstanceOf[BasicValues]
        }
        success
      }
    }

    "encode a line feature collection" in {
      val sft = SimpleFeatureTypes.createType("binlinetest",
        "track:String,label:Long,dtg:Date,dates:List[Date],geom:LineString:srid=4326")
      val line = WKTUtils.read("LINESTRING(45 50, 46 51, 47 52, 50 55)")
      val date = dateFormat.parse("2014-01-01 08:00:00")
      val dates = (0 until 4).map(i => dateFormat.parse(s"2014-01-01 08:00:0${9-i}"))

      val fc = new ListFeatureCollection(sft)
      val builder = new SimpleFeatureBuilder(sft)
      (0 until 1).foreach { i =>
        builder.addAll(Array[AnyRef](s"1234-$i", java.lang.Long.valueOf(i), date, dates, line))
        fc.add(builder.buildFeature(s"$i"))
      }

      "with label field" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dates", Some("track"), Some("label"), None, AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 24, (i + 1) * 24))
          decoded.dtg mustEqual dates(i).getTime
          decoded.lat mustEqual line.getCoordinates()(i).x.toFloat
          decoded.lon mustEqual line.getCoordinates()(i).y.toFloat
          decoded.trackId mustEqual "1234-0".hashCode.toString
          decoded.asInstanceOf[ExtendedValues].label mustEqual 0
        }
        success
      }

      "without label field" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dates", Some("track"), None, None, AxisOrder.LatLon, false)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 16, (i + 1) * 16))
          decoded.dtg mustEqual dates(i).getTime
          decoded.lat mustEqual line.getCoordinates()(i).x.toFloat
          decoded.lon mustEqual line.getCoordinates()(i).y.toFloat
          decoded.trackId mustEqual "1234-0".hashCode.toString
          decoded must beAnInstanceOf[BasicValues]
        }
        success
      }

      "with sorting" >> {
        val out = new ByteArrayOutputStream()
        BinaryOutputEncoder
            .encodeFeatureCollection(fc, out, "dates", Some("track"), None, None, AxisOrder.LatLon, true)
        val encoded = out.toByteArray
        (0 until 4).foreach { i =>
          val decoded = Convert2ViewerFunction.decode(encoded.slice(i * 16, (i + 1) * 16))
          decoded.dtg mustEqual dates(3 - i).getTime
          decoded.lat mustEqual line.getCoordinates()(3 - i).x.toFloat
          decoded.lon mustEqual line.getCoordinates()(3 - i).y.toFloat
          decoded.trackId mustEqual "1234-0".hashCode.toString
          decoded must beAnInstanceOf[BasicValues]
        }
        success
      }
    }
  }
}