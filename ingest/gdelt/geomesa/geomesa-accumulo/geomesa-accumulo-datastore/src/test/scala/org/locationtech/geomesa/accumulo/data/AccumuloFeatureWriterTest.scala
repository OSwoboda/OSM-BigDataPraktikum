/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.data

import java.text.SimpleDateFormat
import java.util.TimeZone

import org.apache.accumulo.core.client.BatchWriterConfig
import org.apache.accumulo.core.data.{Range => aRange}
import org.apache.accumulo.core.security.Authorizations
import org.geotools.data._
import org.geotools.factory.Hints
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.filter.text.cql2.CQL
import org.geotools.filter.text.ecql.ECQL
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.TestWithDataStore
import org.locationtech.geomesa.accumulo.data.tables.{GeoMesaTable, RecordTable}
import org.locationtech.geomesa.accumulo.index.{AttributeIdxStrategy, QueryStrategyDecider}
import org.locationtech.geomesa.features.avro.AvroSimpleFeatureFactory
import org.locationtech.geomesa.features.kryo.KryoFeatureSerializer
import org.locationtech.geomesa.utils.geotools.Conversions._
import org.locationtech.geomesa.utils.text.WKTUtils
import org.opengis.filter.Filter
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType
import scala.collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class AccumuloFeatureWriterTest extends Specification with TestWithDataStore with BeforeExample {

  override def before = clearTablesHard()

  sequential

  val spec = "name:String:index=true,age:Integer,dtg:Date,geom:Geometry:srid=4326"

  val sdf = new SimpleDateFormat("yyyyMMdd")
  sdf.setTimeZone(TimeZone.getTimeZone("Zulu"))
  val dateToIndex = sdf.parse("20140102")
  val geomToIndex = WKTUtils.read("POINT(45.0 49.0)")

  "AccumuloFeatureWriter" should {
    "provide ability to update a single feature that it wrote and preserve feature IDs" in {
      /* create a feature */
      val originalFeature1 = AvroSimpleFeatureFactory.buildAvroFeature(sft, List(), "id1")
      originalFeature1.setDefaultGeometry(geomToIndex)
      originalFeature1.setAttribute("name", "fred")
      originalFeature1.setAttribute("age", 50.asInstanceOf[Any])

      /* create a second feature */
      val originalFeature2 = AvroSimpleFeatureFactory.buildAvroFeature(sft, List(), "id2")
      originalFeature2.setDefaultGeometry(geomToIndex)
      originalFeature2.setAttribute("name", "tom")
      originalFeature2.setAttribute("age", 60.asInstanceOf[Any])

      /* create a third feature */
      val originalFeature3 = AvroSimpleFeatureFactory.buildAvroFeature(sft, List(), "id3")
      originalFeature3.setDefaultGeometry(geomToIndex)
      originalFeature3.setAttribute("name", "kyle")
      originalFeature3.setAttribute("age", 2.asInstanceOf[Any])

      addFeatures(Seq(originalFeature1, originalFeature2, originalFeature3))

      /* turn fred into billy */
      val filter = CQL.toFilter("name = 'fred'")
      fs.modifyFeatures(Array("name", "age"), Array("billy", 25.asInstanceOf[AnyRef]), filter)

      /* delete kyle */
      val deleteFilter = CQL.toFilter("name = 'kyle'")
      fs.removeFeatures(deleteFilter)

      /* query everything */
      val cqlFilter = Filter.INCLUDE

      /* Let's read out what we wrote...we should only get tom and billy back out */
      val features = fs.getFeatures(new Query(sftName, Filter.INCLUDE)).features().toSeq

      features must haveSize(2)
      features.map(f => (f.getAttribute("name"), f.getAttribute("age"))) must
          containTheSameElementsAs(Seq(("tom", 60), ("billy", 25)))
      features.map(f => (f.getAttribute("name"), f.getID)) must
          containTheSameElementsAs(Seq(("tom", "id2"), ("billy", "id1")))
    }

    "be able to replace all features in a store using a general purpose FeatureWriter" in {
      /* repopulate it */
      val c = new DefaultFeatureCollection
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5"))

      val writer = ds.getFeatureWriter(sftName, Transaction.AUTO_COMMIT)

      c.foreach {f =>
        val writerCreatedFeature = writer.next()
        writerCreatedFeature.setAttributes(f.getAttributes)
        writerCreatedFeature.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
        writerCreatedFeature.getUserData.put(Hints.PROVIDED_FID, f.getID)
        writer.write()
      }
      writer.close()

      val features = fs.getFeatures(Filter.INCLUDE).features().toSeq

      features must haveSize(5)

      features.map(f => (f.getAttribute("name"), f.getID)) must
          containTheSameElementsAs(Seq(("will", "fid1"), ("george", "fid2"), ("sue", "fid3"), ("karen", "fid4"), ("bob", "fid5")))
    }

    "be able to update all features based on some ecql" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val filter = CQL.toFilter("(age > 50 AND age < 99) or (name = 'karen')")
      fs.modifyFeatures(Array("age"), Array(60.asInstanceOf[AnyRef]), filter)

      val updated = fs.getFeatures(ECQL.toFilter("age = 60")).features.toSeq

      updated.map(f => (f.getAttribute("name"), f.getAttribute("age"))) must
          containTheSameElementsAs(Seq(("will", 60), ("karen", 60), ("bob", 60)))
      updated.map(f => (f.getAttribute("name"), f.getID)) must
          containTheSameElementsAs(Seq(("will", "fid1"), ("karen", "fid4"), ("bob", "fid5")))
    }

    "provide ability to remove features" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val writer = ds.getFeatureWriter(sftName, Filter.INCLUDE, Transaction.AUTO_COMMIT)
      while (writer.hasNext) {
        writer.next()
        writer.remove()
      }
      writer.close()

      val features = fs.getFeatures(Filter.INCLUDE).features().toSeq
      features must beEmpty

      forall(GeoMesaTable.getTableNames(sft, ds)) { name =>
        val scanner = connector.createScanner(name, new Authorizations())
        try {
          scanner.iterator().hasNext must beFalse
        } finally {
          scanner.close()
        }
      }
    }

    "provide ability to add data inside transactions" in {
      val c = new DefaultFeatureCollection
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Array("dude1", 15.asInstanceOf[AnyRef], null, geomToIndex), "fid10"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Array("dude2", 16.asInstanceOf[AnyRef], null, geomToIndex), "fid11"))
      c.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Array("dude3", 17.asInstanceOf[AnyRef], null, geomToIndex), "fid12"))

      val trans = new DefaultTransaction("trans1")
      fs.setTransaction(trans)
      try {
        fs.addFeatures(c)
        trans.commit()

        val features = fs.getFeatures(ECQL.toFilter("(age = 15) or (age = 16) or (age = 17)")).features().toSeq
        features.map(f => (f.getAttribute("name"), f.getAttribute("age"))) must
            containTheSameElementsAs(Seq(("dude1", 15), ("dude2", 16), ("dude3", 17)))
      } catch {
        case e: Exception =>
          trans.rollback()
          throw e
      } finally {
        trans.close()
        fs.setTransaction(Transaction.AUTO_COMMIT)
      }
    }

    "provide ability to remove inside transactions" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("dude1", 15.asInstanceOf[AnyRef], null, geomToIndex), "fid10"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("dude2", 16.asInstanceOf[AnyRef], null, geomToIndex), "fid11"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("dude3", 17.asInstanceOf[AnyRef], null, geomToIndex), "fid12")
      )
      addFeatures(toAdd)

      val trans = new DefaultTransaction("trans1")
      fs.setTransaction(trans)
      try {
        fs.removeFeatures(CQL.toFilter("name = 'dude1' or name='dude2' or name='dude3'"))
        trans.commit()

        val features = fs.getFeatures(Filter.INCLUDE).features().toSeq

        features must haveSize(3)
        features.map(f => f.getAttribute("name")) must containTheSameElementsAs(Seq("will", "george", "sue"))
      } catch {
        case e: Exception =>
          trans.rollback()
          throw e
      } finally {
        trans.close()
        fs.setTransaction(Transaction.AUTO_COMMIT)
      }
    }

    "issue delete keys when geometry changes" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val filter = CQL.toFilter("name = 'bob' or name = 'karen'")
      val writer = ds.getFeatureWriter(sftName, filter, Transaction.AUTO_COMMIT)

      while (writer.hasNext) {
        val sf = writer.next
        sf.setDefaultGeometry(WKTUtils.read("POINT(50.0 50)"))
        writer.write()
      }
      writer.close()

      // Verify old geo bbox doesn't return them
      val features45 = fs.getFeatures(ECQL.toFilter("BBOX(geom, 44.9,48.9,45.1,49.1)")).features().toSeq
      features45.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("will", "george", "sue"))

      // Verify that new geometries are written with a bbox query that uses the index
      val features50 = fs.getFeatures(ECQL.toFilter("BBOX(geom, 49.9,49.9,50.1,50.1)")).features().toSeq
      features50.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("bob", "karen"))

      // get them all
      val all = fs.getFeatures(ECQL.toFilter("BBOX(geom, 44.0,44.0,51.0,51.0)")).features().toSeq
      all.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("will", "george", "sue", "bob", "karen"))

      // get none
      val none = fs.getFeatures(ECQL.toFilter("BBOX(geom, 30.0,30.0,31.0,31.0)")).features().toSeq
      none must beEmpty
    }

    "issue delete keys when datetime changes" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val filter = CQL.toFilter("name = 'will' or name='george'")
      val writer = ds.getFeatureWriter(sftName, filter, Transaction.AUTO_COMMIT)

      val newDate = sdf.parse("20140202")
      while (writer.hasNext) {
        val sf = writer.next
        sf.setAttribute("dtg", newDate)
        writer.write()
      }
      writer.close()

      // Verify old daterange doesn't return them
      val jan = fs.getFeatures(ECQL.toFilter("dtg DURING 2013-12-29T00:00:00Z/2014-01-04T00:00:00Z")).features().toSeq
      jan.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("sue", "bob", "karen"))

      // Verify new date range returns things
      val feb = fs.getFeatures(ECQL.toFilter("dtg DURING 2014-02-01T00:00:00Z/2014-02-03T00:00:00Z")).features().toSeq
      feb.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("will","george"))

      // Verify large date range returns everything
      val all = fs.getFeatures(ECQL.toFilter("dtg DURING 2014-01-01T00:00:00Z/2014-02-03T00:00:00Z")).features().toSeq
      all.map(_.getAttribute("name")) must containTheSameElementsAs(Seq("will", "george", "sue", "bob", "karen"))

      // Verify other date range returns nothing
      val none = fs.getFeatures(ECQL.toFilter("dtg DURING 2013-01-01T00:00:00Z/2013-12-31T00:00:00Z")).features().toSeq
      none must beEmpty
    }

    "verify that start end times are excluded in filter" in { // TODO this should be moved somewhere else...
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val afterFilter = fs.getFeatures(ECQL.toFilter("dtg AFTER 2014-02-02T00:00:00Z")).features.toSeq
      afterFilter must beEmpty

      val beforeFilter = fs.getFeatures(ECQL.toFilter("dtg BEFORE 2014-01-02T00:00:00Z")).features.toSeq
      beforeFilter must beEmpty
    }

    "ensure that feature IDs are not changed when spatiotemporal indexes change" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val writer = ds.getFeatureWriter(sftName, Filter.INCLUDE, Transaction.AUTO_COMMIT)
      val newDate = sdf.parse("20120102")
      while (writer.hasNext) {
        val sf = writer.next
        sf.setAttribute("dtg", newDate)
        sf.setDefaultGeometry(WKTUtils.read("POINT(10.0 10.0)"))
        writer.write()
      }
      writer.close()

      val features = fs.getFeatures(Filter.INCLUDE).features().toSeq

      features.size mustEqual toAdd.size

      val compare = features.sortBy(_.getID).zip(toAdd.sortBy(_.getID))
      forall(compare) { case (updated, original) =>
        updated.getID mustEqual original.getID
        updated.getDefaultGeometry must not be equalTo(original.getDefaultGeometry)
        updated.getAttribute("dtg") must not be equalTo(original.getAttribute("dtg"))
      }
    }

    "verify delete and add same key works" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      addFeatures(toAdd)

      val filter = CQL.toFilter("name = 'will'")

      val hints = ds.strategyHints(sft)
      val q = new Query(sft.getTypeName, filter)
      QueryStrategyDecider.chooseStrategies(sft, q, hints, None).head must beAnInstanceOf[AttributeIdxStrategy]

      import org.locationtech.geomesa.utils.geotools.Conversions._

      // Retrieve Will's ID before deletion.
      val featuresBeforeDelete = fs.getFeatures(filter).features().toSeq

      featuresBeforeDelete must haveSize(1)
      val willId = featuresBeforeDelete.head.getID

      fs.removeFeatures(filter)

      // NB: We really need a test which reads from the attribute table directly since missing records entries
      //  will result in attribute queries
      // This verifies that 'will' has been deleted from the attribute table.
      val attributeTableFeatures = fs.getFeatures(filter).features().toSeq
      attributeTableFeatures must beEmpty

      // This verifies that 'will' has been deleted from the record table.
      val recordTableFeatures =fs.getFeatures(ECQL.toFilter(s"IN('$willId')")).features().toSeq
      recordTableFeatures must beEmpty

      // This verifies that 'will' has been deleted from the ST idx table.
      val stTableFeatures = fs.getFeatures(ECQL.toFilter("BBOX(geom, 44.0,44.0,51.0,51.0)")).features().toSeq
      stTableFeatures.count(_.getID == willId) mustEqual 0

      val featureCollection = new DefaultFeatureCollection(sftName, sft)
      val geom = WKTUtils.read("POINT(10.0 10.0)")
      val date = sdf.parse("20120102")
      /* create a feature */
      featureCollection.add(AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], date, geom), "fid1"))
      fs.addFeatures(featureCollection)

      val features =fs.getFeatures(filter).features().toSeq
      features must haveSize(1)
    }

    "create z3 based uuids" in {
      val toAdd = Seq(
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("will", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid1"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("george", 33.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid2"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("sue", 99.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid3"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("karen", 50.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid4"),
        AvroSimpleFeatureFactory.buildAvroFeature(sft, Seq("bob", 56.asInstanceOf[AnyRef], dateToIndex, geomToIndex), "fid5")
      )
      // space out the adding slightly so we ensure they sort how we want - resolution is to the ms
      // also ensure we don't set use_provided_fid
      toAdd.foreach { f =>
        val featureCollection = new DefaultFeatureCollection(sftName, sft)
        f.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.FALSE)
        f.getUserData.remove(Hints.PROVIDED_FID)
        featureCollection.add(f)
        // write the feature to the store
        fs.addFeatures(featureCollection)
        Thread.sleep(2)
      }

      val scanner = ds.connector.createScanner(ds.getTableName(sftName, RecordTable), new Authorizations)
      val serializer = new KryoFeatureSerializer(sft)
      val rows = scanner.toList
      scanner.close()

      // trim off table prefix to get the UUIDs
      val rowKeys = rows.map(_.getKey.getRow.toString).map(r => r.substring(r.length - 36))
      rowKeys must haveLength(5)

      // ensure that the z3 range is the same
      rowKeys.map(_.substring(0, 18)).toSet must haveLength(1)
      // ensure that the second part of the UUID is random
      rowKeys.map(_.substring(19)).toSet must haveLength(5)

      val ids = rows.map(e => serializer.deserialize(e.getValue.get).getID)
      ids must haveLength(5)
      forall(ids)(_ must not(beMatching("fid\\d")))
      // ensure they share a common prefix, since they have the same dtg/geom
      ids.map(_.substring(0, 18)).toSet must haveLength(1)
      // ensure that the second part of the UUID is random
      ids.map(_.substring(19)).toSet must haveLength(5)
    }
  }

  def clearTablesHard(): Unit = {
    GeoMesaTable.getTables(sft).map(ds.getTableName(sft.getTypeName, _)).foreach { name =>
      val deleter = connector.createBatchDeleter(name, new Authorizations(), 5, new BatchWriterConfig())
      deleter.setRanges(Seq(new aRange()))
      deleter.delete()
      deleter.close()
    }
  }
}
