/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.index

import com.vividsolutions.jts.geom._
import org.apache.accumulo.core.data.Key
import org.apache.hadoop.io.Text
import org.joda.time.{DateTime, DateTimeZone}
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo._
import org.locationtech.geomesa.accumulo.data.AccumuloFeatureWriter.FeatureToWrite
import org.locationtech.geomesa.features.avro.AvroSimpleFeatureFactory
import org.locationtech.geomesa.features.{SerializationType, SimpleFeatureSerializers}
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.locationtech.geomesa.utils.text.WKTUtils
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.util.Try

@RunWith(classOf[JUnitRunner])
class IndexSchemaTest extends Specification {

  val dummyType = SimpleFeatureTypes.createType("DummyType",s"foo:String,bar:Geometry,baz:Date,$DEFAULT_GEOMETRY_PROPERTY_NAME:Geometry,$DEFAULT_DTG_PROPERTY_NAME:Date,dtg_end_time:Date")
  val customType = SimpleFeatureTypes.createType("DummyType",s"foo:String,bar:Geometry,baz:Date,*the_geom:Geometry,dt_start:Date,dtg_end_time:Date")
  customType.setDtgField("dt_start")
  val dummyEncoder = SimpleFeatureSerializers(dummyType, SerializationType.AVRO)
  val customEncoder = SimpleFeatureSerializers(customType, SerializationType.AVRO)
  val dummyIndexValueEncoder = IndexValueEncoder(dummyType)

  "SpatioTemporalIndexSchemaTest" should {
    "parse a valid string" in {
      val schema = IndexSchema.buildKeyEncoder(dummyType, "%~#s%foo#cstr%99#r::%~#s%0,4#gh::%~#s%4,3#gh%15#id")
      schema should not be null
    }

    "allow geohash in the row" in {
      val s = "%~#s%foo#cstr%0,1#gh%99#r::%~#s%1,5#gh::%~#s%5,2#gh%15#id"
      val matched = IndexSchema.buildKeyPlanner(s) match {
        case CompositePlanner(List(ConstStringPlanner("foo"), GeoHashKeyPlanner(0,1), RandomPartitionPlanner(99)),"~") => true
        case _ => false
      }
      matched must beTrue
    }

    "allow extra elements inside the column qualifier" in {
      val schema = Try(IndexSchema.buildKeyEncoder(dummyType,
        "%~#s%foo#cstr%0,1#gh%99#r::%~#s%1,5#gh::%~#s%9#r%ColQ#cstr%15#id%5,2#gh"))
      schema.isFailure must beTrue
    }

    "complain when there are extra elements at the end" in {
      val schema = Try(IndexSchema.buildKeyEncoder(dummyType,
        "%~#s%foo#cstr%0,1#gh%99#r::%~#s%1,5#gh::%~#s%15#id%5,2#gh"))
      schema.isFailure must beTrue
    }

    "allow index/data flags" in {
      val schema = IndexSchema.buildKeyEncoder(dummyType,
        "%~#s%#i%foo#cstr%99#r::%~#s%0,4#gh::%~#s%4,3#gh%15#id")
      schema should not be null
    }
  }

  val geometryFactory = new GeometryFactory(new PrecisionModel, 4326)

  val now = new DateTime().toDate

  val Apr_23_2001 = new DateTime(2001, 4, 23, 12, 5, 0, DateTimeZone.forID("UTC")).toDate

  val schemaEncoding = "%~#s%feature#cstr%99#r::%~#s%0,4#gh::%~#s%4,3#gh%#id"
  val index = IndexSchema.buildKeyEncoder(dummyType, schemaEncoding)

  "single-point (-78.4953560 38.0752150)" should {
    "encode to 2 index rows" in {
      val point : Geometry = WKTUtils.read("POINT(-78.4953560 38.0752150)")
      val item = AvroSimpleFeatureFactory.buildAvroFeature(dummyType, List("TEST_POINT", point, now, point, now, now), "TEST_POINT")
      val toWrite = new FeatureToWrite(item, "", dummyEncoder, dummyIndexValueEncoder)
      val indexEntries = index.encode(toWrite).toList
      indexEntries.size must equalTo(1)
      indexEntries.head.size() mustEqual(2)
      val cf = new Text(indexEntries.head.getUpdates.get(0).getColumnFamily)
      val cq = new Text(indexEntries.head.getUpdates.get(0).getColumnQualifier)
      val keyStr = cf + "::" + cq
      keyStr must equalTo("dqb0::tg3~TEST_POINT")
    }
  }

  "line (Cherry Avenue segment)" should {
    "encode to 6 index rows" in {
      val line : Geometry = WKTUtils.read("LINESTRING(-78.5000092574703 38.0272986617359,-78.5000196719491 38.0272519798381,-78.5000300864205 38.0272190279085,-78.5000370293904 38.0271853867342,-78.5000439723542 38.027151748305,-78.5000509153117 38.027118112621,-78.5000578582629 38.0270844741902,-78.5000648011924 38.0270329867966,-78.5000648011781 38.0270165108316,-78.5000682379314 38.026999348366,-78.5000752155953 38.026982185898,-78.5000786870602 38.0269657099304,-78.5000856300045 38.0269492339602,-78.5000891014656 38.0269327579921,-78.5000960444045 38.0269162820211,-78.5001064588197 38.0269004925451,-78.5001134017528 38.0268847030715,-78.50012381616 38.0268689135938,-78.5001307590877 38.0268538106175,-78.5001411734882 38.0268387076367,-78.5001550593595 38.0268236046505,-78.5001654737524 38.0268091881659,-78.5001758881429 38.0267954581791,-78.5001897740009 38.0267810416871,-78.50059593303 38.0263663951609,-78.5007972751677 38.0261625038609)")
      val item = AvroSimpleFeatureFactory.buildAvroFeature(dummyType, List("TEST_LINE", line, now, line, now, now), "TEST_LINE")
      val toWrite = new FeatureToWrite(item, "", dummyEncoder, dummyIndexValueEncoder)
      val indexEntries = index.encode(toWrite).toList
      indexEntries.size must equalTo(1)
      indexEntries.head.size() mustEqual(6)
      val cf = new Text(indexEntries.head.getUpdates.get(0).getColumnFamily)
      val cq = new Text(indexEntries.head.getUpdates.get(0).getColumnQualifier)
      val keyStr = cf + "::" + cq
      keyStr must equalTo("dqb0::mdw~TEST_LINE")
    }
  }

  "polygon (Virginia)" should {
    "encode to 6 index rows" in {
      val polygon : Geometry = WKTUtils.read("POLYGON((-77.286202 38.347024,-77.249701 38.331624,-77.179339 38.341914,-77.138224 38.367916,-77.084238 38.368739,-77.041505 38.400692,-77.011464 38.374425,-77.028131 38.305317,-76.962086 38.256964,-76.94902 38.208418,-76.839368 38.163609,-76.60543 38.148933,-76.596156 38.106676,-76.536457 38.074903,-76.516609 38.026783,-76.465008 38.01322,-76.412934 37.966332,-76.333372 37.942956,-76.236458 37.886605,-76.052021 37.953578,-75.943693 37.946133,-75.952672 37.906827,-75.80124 37.912174,-75.761642 37.941366,-75.704634 37.954698,-75.6477 37.946963,-75.624449 37.994195,-75.166435 38.027834,-75.303132 37.838169,-75.360125 37.810186,-75.417073 37.82105,-75.460933 37.771356,-75.541779 37.606145,-75.529901 37.556717,-75.58911 37.477857,-75.604026 37.422024,-75.643456 37.372864,-75.644386 37.34227,-75.715515 37.275687,-75.729271 37.216901,-75.766049 37.154932,-75.889676 37.054515,-75.928307 36.938179,-75.874647 36.744305,-75.797497 36.550916,-80.122173 36.54265,-81.677393 36.588156,-81.6469 36.611918,-81.922644 36.616213,-81.934144 36.594213,-83.675395 36.600784,-83.530146 36.666049,-83.424713 36.66734,-83.309711 36.710639,-83.13512 36.742629,-83.133231 36.784585,-83.072836 36.854457,-83.006577 36.847643,-82.878683 36.889532,-82.857665 36.928669,-82.867467 36.978049,-82.722433 37.044949,-82.721413 37.12083,-82.498747 37.226958,-82.348757 37.267944,-81.968012 37.538035,-81.926243 37.513574,-81.996075 37.471526,-81.987006 37.454878,-81.935621 37.438397,-81.922956 37.411389,-81.936744 37.38073,-81.928462 37.360461,-81.860489 37.315942,-81.849949 37.285227,-81.757137 37.276147,-81.740124 37.237752,-81.723061 37.240493,-81.678221 37.20154,-81.554705 37.20799,-81.507636 37.233712,-81.499096 37.257952,-81.480144 37.251121,-81.416029 37.273626,-81.365284 37.337842,-81.225104 37.234874,-81.104147 37.280605,-80.979589 37.302279,-80.966556 37.292158,-80.89916 37.315678,-80.849451 37.346909,-80.883247 37.383933,-80.859457 37.429491,-80.784188 37.394587,-80.770082 37.372363,-80.552036 37.473563,-80.511391 37.481672,-80.49486 37.435072,-80.475601 37.422949,-80.309333 37.502883,-80.282391 37.533519,-80.330368 37.536267,-80.312201 37.54622,-80.328504 37.564315,-80.220984 37.627767,-80.267504 37.646156,-80.295916 37.692838,-80.253077 37.725899,-80.257411 37.756084,-80.216605 37.775961,-80.22769 37.797892,-80.162202 37.875122,-79.999383 37.994908,-79.926397 38.106543,-79.945258 38.132239,-79.918662 38.15479,-79.920075 38.182529,-79.788945 38.268703,-79.810154 38.306707,-79.764432 38.356514,-79.725597 38.363828,-79.729512 38.381194,-79.689667 38.431462,-79.697572 38.487223,-79.662174 38.515217,-79.669775 38.550316,-79.649175 38.591415,-79.53687 38.550917,-79.476638 38.457228,-79.312276 38.411876,-79.28294 38.4181,-79.210591 38.492913,-79.129756 38.655018,-79.092755 38.659817,-79.085455 38.724614,-79.054954 38.785713,-79.027253 38.792113,-78.999752 38.846162,-78.868971 38.763139,-78.786025 38.887187,-78.717076 38.936028,-78.719291 38.90523,-78.697437 38.914359,-78.627798 38.98179,-78.601655 38.964603,-78.550467 39.018065,-78.571901 39.031995,-78.403697 39.167451,-78.438695 39.198093,-78.399669 39.243874,-78.419422 39.257476,-78.339284 39.348605,-78.366867 39.35929,-78.343214 39.388807,-78.359918 39.409028,-78.347087 39.466012,-77.828299 39.132426,-77.730697 39.315603,-77.67681 39.324558,-77.61591 39.302548,-77.566598 39.306047,-77.543086 39.266916,-77.45771 39.224979,-77.478672 39.189671,-77.517622 39.169417,-77.520092 39.120869,-77.485401 39.10931,-77.456971 39.073399,-77.337137 39.062265,-77.248419 39.026554,-77.24503 38.982684,-77.147197 38.964439,-77.041672 38.872533,-77.043877 38.717674,-77.122632 38.685256,-77.129682 38.634704,-77.246362 38.593441,-77.310558 38.493993,-77.32291 38.432625,-77.286202 38.347024))")
      val item = AvroSimpleFeatureFactory.buildAvroFeature(dummyType, List("TEST_POLYGON", polygon, now, polygon, now, now), "TEST_POLYGON")
      val toWrite = new FeatureToWrite(item, "", dummyEncoder, dummyIndexValueEncoder)
      val indexEntries = index.encode(toWrite).toList
      indexEntries.size must equalTo(1)
      indexEntries.head.size() mustEqual(6)
      val cf = new Text(indexEntries.head.getUpdates.get(0).getColumnFamily)
      val cq = new Text(indexEntries.head.getUpdates.get(0).getColumnQualifier)
      val keyStr = cf + "::" + cq
      keyStr must equalTo("dn..::...~TEST_POLYGON")
    }
  }

  "index-entry encoded and decoder" should {
    "encode and decode round-trip properly using a custom date-time field name" in {
      // inputs
      val wkt = "POINT (-78.495356 38.075215)"
      val id = "Feature0123456789"
      val geom = WKTUtils.read(wkt)
      val dt = Apr_23_2001
      val entry = AvroSimpleFeatureFactory.buildAvroFeature(customType, List(id, geom, dt, geom, dt, dt), id)
      val schema = "%~#s%99#r%#i%TEST#cstr%0,3#gh%yyyyMMdd#d::%~#s%3,2#gh::%~#s%#id"
      val encoder = IndexSchema.buildKeyEncoder(customType, schema)
      val decoder = IndexSchema.getIndexEntryDecoder(schema)

      val toWrite = new FeatureToWrite(entry, "", customEncoder, dummyIndexValueEncoder)
      val encodedKVs = encoder.encode(toWrite).map { m =>
        new Key(new Text(m.getRow))
      }

      // requirements
      encodedKVs must not beNull;
      encodedKVs.size must be equalTo 2

      // return trip
      val decoded = decoder.decode(encodedKVs.head)

      // requirements
      decoded must not equalTo null

      // the GeoHash decoded must contain the initial point geometry
      val geomOut = decoded.getDefaultGeometry.asInstanceOf[Geometry]
      geomOut.contains(geom) must beTrue

      // the decoded date will only be accurate to the (year, month, day)
      // (but beware time-zone effects for direct comparison!)
      val dtOut = decoded.getAttribute(DEFAULT_DTG_PROPERTY_NAME).asInstanceOf[Option[DateTime]].getOrElse(
        throw new Exception("Invalid date field.")).toDate
      // time should be off by 12 hours and 5 minutes
      // (the portion beyond "yyyyMMdd")
      val msDelta = ((12L * 60L) + 5L) * 60L * 1000L
      (Math.abs(dtOut.getTime - Apr_23_2001.getTime) == msDelta) must beTrue
    }
  }

  "IndexSchemaBuilder" should {
    "be able to create index schemas" in {
      val maxShard = 31
      val name = "test"
      val oldSchema = s"%~#s%$maxShard#r%$name#cstr%0,3#gh%yyyyMMdd#d::%~#s%3,2#gh::%~#s%#id"

      val schema = new IndexSchemaBuilder("~")
                   .randomNumber(maxShard)
                   .constant(name)
                   .geoHash(0, 3)
                   .date("yyyyMMdd")
                   .nextPart()
                   .geoHash(3, 2)
                   .nextPart()
                   .id()
                   .build()

      schema must be equalTo oldSchema
    }
  }
}
