/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.index

import java.util.AbstractMap.SimpleEntry

import org.apache.accumulo.core.data.{Key, Value}
import org.apache.hadoop.io.Text
import org.geotools.data.Query
import org.geotools.factory.CommonFactoryFinder
import org.junit.runner.RunWith
import org.locationtech.geomesa.CURRENT_SCHEMA_VERSION
import org.locationtech.geomesa.accumulo.TestWithDataStore
import org.locationtech.geomesa.features.{ScalaSimpleFeature, SerializationType, SimpleFeatureSerializers}
import org.locationtech.geomesa.security._
import org.opengis.filter.sort.SortBy
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class QueryPlannerTest extends Specification with Mockito with TestWithDataStore {

  override val spec = "*geom:Geometry,dtg:Date,s:String"
  val schema = ds.getIndexSchemaFmt(sftName)
  val sf = new ScalaSimpleFeature("id", sft)
  sf.setAttributes(Array[AnyRef]("POINT(45 45)", "2014/10/10T00:00:00Z", "string"))
  val sf2 = new ScalaSimpleFeature("id2", sft)
  sf2.setAttributes(Array[AnyRef]("POINT(45 45)", "2014/10/10T00:00:00Z", "astring"))

  addFeatures(Seq(sf, sf2))

  "adaptStandardIterator" should {
    "return a LazySortedIterator when the query has an order by clause" >> {
      val query = new Query(sft.getTypeName)
      query.setSortBy(Array(SortBy.NATURAL_ORDER))

      val planner = new QueryPlanner(sft, SerializationType.KRYO, schema, ds, NoOpHints)
      val result = planner.runQuery(query)

      result must beAnInstanceOf[LazySortedIterator]
    }

    "not return a LazySortedIterator when the query does not have an order by clause" >> {
      val query = new Query(sft.getTypeName)
      query.setSortBy(null)

      val planner = new QueryPlanner(sft, SerializationType.KRYO, schema, ds, NoOpHints)
      val result = planner.runQuery(query)

      result must not (beAnInstanceOf[LazySortedIterator])
    }

    "decode and set visibility properly" >> {
      val query = new Query(sft.getTypeName)
      val planner = new QueryPlanner(sft, SerializationType.KRYO, schema, ds, NoOpHints)
      QueryPlanner.configureQuery(query, sft) // have to do manually

      val visibilities = Array("", "USER", "ADMIN")
      val expectedVis = visibilities.map(vis => if (vis.isEmpty) None else Some(vis))

      val serializer = SimpleFeatureSerializers(sft, SerializationType.KRYO)

      val value = new Value(serializer.serialize(sf))
      val kvs =  visibilities.zipWithIndex.map { case (vis, ndx) =>
        val key = new Key(new Text(ndx.toString), new Text("cf"), new Text("cq"), new Text(vis))
        new SimpleEntry[Key, Value](key, value)
      }

      val expectedResult = kvs.map(planner.defaultKVsToFeatures(query.getHints)).map(_.visibility)

      expectedResult must haveSize(kvs.length)
      expectedResult mustEqual expectedVis
    }

    "sort with a projected SFT" >> {
      val ff = CommonFactoryFinder.getFilterFactory2
      val query = new Query(sft.getTypeName)
      query.setSortBy(Array(SortBy.NATURAL_ORDER))
      query.setProperties(List(ff.property("s")))

      val planner = new QueryPlanner(sft, SerializationType.KRYO, schema, ds, NoOpHints)
      val result = planner.runQuery(query).toList

      result.map(_.getID) mustEqual Seq("id", "id2")
      forall(result)(r => r.getAttributeCount mustEqual 2) // geom always gets included
      result.map(_.getAttribute("s")) must containTheSameElementsAs(Seq("string", "astring"))

    }
  }
}
