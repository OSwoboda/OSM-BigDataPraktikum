/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.iterators

import com.vividsolutions.jts.geom.Envelope
import org.apache.accumulo.core.client.mock.MockInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.hadoop.io.Text
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.data.{DataStore, DataUtilities, Query}
import org.geotools.factory.Hints
import org.geotools.filter.text.ecql.ECQL
import org.geotools.filter.visitor.ExtractBoundsFilterVisitor
import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.data._
import org.locationtech.geomesa.accumulo.index.{Constants, QueryHints}
import org.locationtech.geomesa.accumulo.iterators.TemporalDensityIterator.{TIME_SERIES, decodeTimeSeries, jsonToTimeSeries}
import org.locationtech.geomesa.features.avro.AvroSimpleFeatureFactory
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConversions._


@RunWith(classOf[JUnitRunner])
class TemporalDensityIteratorTest extends Specification {

  sequential

  import org.locationtech.geomesa.utils.geotools.Conversions._

  def createDataStore(sft: SimpleFeatureType, i: Int = 0): DataStore = {
    val testTableName = "tdi_test"

    val dsf = new AccumuloDataStoreFactory

    import org.locationtech.geomesa.accumulo.data.AccumuloDataStoreParams._

    val ds = dsf.createDataStore(Map(
      zookeepersParam.key -> "dummy",
      instanceIdParam.key -> f"dummy$i%d",
      userParam.key       -> "user",
      passwordParam.key   -> "pass",
      tableNameParam.key  -> testTableName,
      mockParam.key       -> "true"))
    ds.createSchema(sft)
    ds
  }

  def loadFeatures(ds: DataStore, sft: SimpleFeatureType, encodedFeatures: Array[_ <: Array[_]]): SimpleFeatureStore = {
    val builder = AvroSimpleFeatureFactory.featureBuilder(sft)

    def decodeFeature(e: Array[_]): SimpleFeature = {
      val f = builder.buildFeature(e(0).toString, e.asInstanceOf[Array[AnyRef]])
      f.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
      f.getUserData.put(Hints.PROVIDED_FID, e(0).toString)
      f
    }

    val features = encodedFeatures.map(decodeFeature)

    val fs = ds.getFeatureSource("test").asInstanceOf[SimpleFeatureStore]
    fs.addFeatures(DataUtilities.collection(features))
    fs.getTransaction.commit()
    fs
  }

  def getQuery(query: String): Query = {
    val q = new Query("test", ECQL.toFilter(query))
    val geom = q.getFilter.accept(ExtractBoundsFilterVisitor.BOUNDS_VISITOR, null).asInstanceOf[Envelope]
    q.getHints.put(QueryHints.TEMPORAL_DENSITY_KEY, java.lang.Boolean.TRUE)
    q.getHints.put(QueryHints.TIME_INTERVAL_KEY, new Interval(new DateTime("2012-01-01T0:00:00", DateTimeZone.UTC).getMillis, new DateTime("2012-01-02T0:00:00", DateTimeZone.UTC).getMillis))
    q.getHints.put(QueryHints.TIME_BUCKETS_KEY, 24)
    q.getHints.put(QueryHints.RETURN_ENCODED, java.lang.Boolean.TRUE)
    q
  }

  def getQueryJSON(query: String): Query = {
    val q = new Query("test", ECQL.toFilter(query))
    val geom = q.getFilter.accept(ExtractBoundsFilterVisitor.BOUNDS_VISITOR, null).asInstanceOf[Envelope]
    q.getHints.put(QueryHints.TEMPORAL_DENSITY_KEY, java.lang.Boolean.TRUE)
    q.getHints.put(QueryHints.TIME_INTERVAL_KEY, new Interval(new DateTime("2012-01-01T0:00:00", DateTimeZone.UTC).getMillis, new DateTime("2012-01-02T0:00:00", DateTimeZone.UTC).getMillis))
    q.getHints.put(QueryHints.TIME_BUCKETS_KEY, 24)
    q
  }

  "TemporalDensityIterator" should {
    val spec = "id:java.lang.Integer,attr:java.lang.Double,dtg:Date,geom:Geometry:srid=4326"
    val sft = SimpleFeatureTypes.createType("test", spec)
    val builder = AvroSimpleFeatureFactory.featureBuilder(sft)
    sft.getUserData.put(Constants.SF_PROPERTY_START_TIME, "dtg")
    val ds = createDataStore(sft,0)
    val encodedFeatures = (0 until 150).toArray.map{
      i => Array(i.toString, "1.0", new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate, "POINT(-77 38)")
    }
    val fs = loadFeatures(ds, sft, encodedFeatures)

    "reduce total features returned" in {
      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")
      val results = fs.getFeatures(q)
      val allFeatures = results.features()
      val iter = allFeatures.toList
      (iter must not).beNull

      iter.length should be lessThan 150
      iter.length mustEqual 1
    }

    "maintain total weights of time" in {

      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val iter = results.features().toList
      val sf = iter.head.asInstanceOf[SimpleFeature]
      iter must not beNull

      val timeSeries = decodeTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])
      val totalCount = timeSeries.map { case (dateTime, count) => count}.sum

      totalCount mustEqual 150
      timeSeries.size mustEqual 1
    }

    "maintain total weights of time - json" in {

      val q = getQueryJSON("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val iter = results.features().toList
      val sf = iter.head.asInstanceOf[SimpleFeature]
      iter must not beNull

      val timeSeries = jsonToTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])
      val totalCount = timeSeries.map { case (dateTime, count) => count}.sum

      totalCount mustEqual 150
      timeSeries.size mustEqual 1
    }


    "maintain total irrespective of point" in {
      val ds = createDataStore(sft, 1)
      val encodedFeatures = (0 until 150).toArray.map {
        i => Array(i.toString, "1.0", new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate, s"POINT(-77.$i 38.$i)")
      }
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val sfList = results.features().toList

      val sf = sfList.head.asInstanceOf[SimpleFeature]
      val timeSeries = decodeTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])

      val total = timeSeries.map { case (dateTime, count) => count}.sum

      total mustEqual 150
      timeSeries.size mustEqual 1
    }

    "maintain total irrespective of point - json" in {
      val ds = createDataStore(sft, 2)
      val encodedFeatures = (0 until 150).toArray.map {
        i => Array(i.toString, "1.0", new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate, s"POINT(-77.$i 38.$i)")
      }
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQueryJSON("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val sfList = results.features().toList

      val sf = sfList.head.asInstanceOf[SimpleFeature]
      val timeSeries = jsonToTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])

      val total = timeSeries.map { case (dateTime, count) => count}.sum

      total mustEqual 150
      timeSeries.size mustEqual 1
    }

    "correctly bin off of time intervals" in {
      val ds = createDataStore(sft, 3)
      val encodedFeatures = (0 until 48).toArray.map {
        i => Array(i.toString, "1.0", new DateTime(s"2012-01-01T${i%24}:00:00", DateTimeZone.UTC).toDate, "POINT(-77 38)")
      }
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")


      val results = fs.getFeatures(q)
      val sf = results.features().toList.head.asInstanceOf[SimpleFeature]
      val timeSeries = decodeTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])

      val total = timeSeries.map {
        case (dateTime, count) =>
          count mustEqual 2L
          count}.sum

      total mustEqual 48
      timeSeries.size mustEqual 24
    }

    "correctly bin off of time intervals - json" in {
      val ds = createDataStore(sft, 4)
      val encodedFeatures = (0 until 48).toArray.map {
        i => Array(i.toString, "1.0", new DateTime(s"2012-01-01T${i%24}:00:00", DateTimeZone.UTC).toDate, "POINT(-77 38)")
      }
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQueryJSON("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")


      val results = fs.getFeatures(q)
      val sf = results.features().toList.head.asInstanceOf[SimpleFeature]
      val timeSeries = jsonToTimeSeries(sf.getAttribute(TIME_SERIES).asInstanceOf[String])

      val total = timeSeries.map {
        case (dateTime, count) =>
          count mustEqual 2L
          count}.sum

      total mustEqual 48
      timeSeries.size mustEqual 24
    }

    "encode decode feature" in {
      var timeSeries = new collection.mutable.HashMap[DateTime, Long]()
      timeSeries.put(new DateTime("2012-01-01T00:00:00", DateTimeZone.UTC), 2)
      timeSeries.put(new DateTime("2012-01-01T01:00:00", DateTimeZone.UTC), 8)

      val encoded = TemporalDensityIterator.encodeTimeSeries(timeSeries)
      val decoded = TemporalDensityIterator.decodeTimeSeries(encoded)

      timeSeries mustEqual decoded
      timeSeries.size mustEqual 2
      timeSeries.get(new DateTime("2012-01-01T00:00:00", DateTimeZone.UTC)).get mustEqual 2L
      timeSeries.get(new DateTime("2012-01-01T01:00:00", DateTimeZone.UTC)).get mustEqual 8L
    }

    "query dtg bounds not in DataStore" in {
      val ds = createDataStore(sft, 5)
      val encodedFeatures = (0 until 48).toArray.map {
        i => Array(i.toString, "1.0", new DateTime(s"2012-02-01T${i%24}:00:00", DateTimeZone.UTC).toDate, "POINT(-77 38)")
      }
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val sfList = results.features().toList
      sfList.length mustEqual 0
    }

    "nothing to query over" in {
      val ds = createDataStore(sft, 6)
      val encodedFeatures = new Array[Array[_]](0)
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQuery("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val sfList = results.features().toList
      sfList.length mustEqual 0
    }

    "nothing to query over - json" in {
      val ds = createDataStore(sft, 7)
      val encodedFeatures = new Array[Array[_]](0)
      val fs = loadFeatures(ds, sft, encodedFeatures)

      val q = getQueryJSON("(dtg between '2012-01-01T00:00:00.000Z' AND '2012-01-02T00:00:00.000Z') and BBOX(geom, -80, 33, -70, 40)")

      val results = fs.getFeatures(q)
      val sfList = results.features().toList
      sfList.length mustEqual 0
    }
  }
}
