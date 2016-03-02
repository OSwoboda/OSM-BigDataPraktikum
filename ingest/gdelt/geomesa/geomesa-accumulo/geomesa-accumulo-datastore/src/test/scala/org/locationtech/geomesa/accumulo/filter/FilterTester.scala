/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.filter

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.geom.Coordinate
import org.geotools.data.Query
import org.geotools.factory.{CommonFactoryFinder, Hints}
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.filter.text.ecql.ECQL
import org.geotools.geometry.jts.JTSFactoryFinder
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.TestWithDataStore
import org.locationtech.geomesa.accumulo.filter.TestFilters._
import org.locationtech.geomesa.accumulo.iterators.TestData
import org.locationtech.geomesa.features.ScalaSimpleFeature
import org.locationtech.geomesa.features.avro.AvroSimpleFeatureFactory
import org.locationtech.geomesa.utils.geotools.Conversions._
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.opengis.feature.simple.SimpleFeature
import org.opengis.filter._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class FilterTester extends Specification with TestWithDataStore with LazyLogging {

  override val spec = SimpleFeatureTypes.encodeType(TestData.featureType)

  val mediumDataFeatures: Seq[SimpleFeature] =
    TestData.mediumData.map(TestData.createSF).map(f => new ScalaSimpleFeature(f.getID, sft, f.getAttributes.toArray))

  addFeatures(mediumDataFeatures)

  "Filters" should {
    "filter correctly for all predicates" >> {
      runTest(goodSpatialPredicates)
    }

    "filter correctly for AND geom predicates" >> {
      runTest(andedSpatialPredicates)
    }

    "filter correctly for OR geom predicates" >> {
      runTest(oredSpatialPredicates)
    }

    "filter correctly for OR geom predicates with projections" >> {
      runTest(oredSpatialPredicates, Array("geom"))
    }

    "filter correctly for basic temporal predicates" >> {
      runTest(temporalPredicates)
    }

    "filter correctly for basic spatiotemporal predicates" >> {
      runTest(spatioTemporalPredicates)
    }

    "filter correctly for basic spariotemporal predicates with namespaces" >> {
      runTest(spatioTemporalPredicatesWithNS)
    }

    "filter correctly for attribute predicates" >> {
      runTest(attributePredicates)
    }

    "filter correctly for attribute and geometric predicates" >> {
      runTest(attributeAndGeometricPredicates)
    }

    "filter correctly for attribute and geometric predicates with namespaces" >> {
      runTest(attributeAndGeometricPredicatesWithNS)
    }

    "filter correctly for DWITHIN predicates" >> {
      runTest(dwithinPointPredicates)
    }

    "filter correctly for ID predicates" >> {
      runTest(idPredicates)
    }
  }

  def compareFilter(filter: Filter, projection: Array[String]) = {
    val filterCount = mediumDataFeatures.count(filter.evaluate)
    val query = new Query(sftName, filter)
    Option(projection).foreach(query.setPropertyNames)
    val queryCount = fs.getFeatures(query).size
    logger.debug(s"\nFilter: ${ECQL.toCQL(filter)}\nFullData size: ${mediumDataFeatures.size}: " +
        s"filter hits: $filterCount query hits: $queryCount")
    queryCount mustEqual filterCount
  }

  def runTest(filters: Seq[String], projection: Array[String] = null) =
    forall(filters.map(ECQL.toFilter))(compareFilter(_, projection))
}

@RunWith(classOf[JUnitRunner])
class IdQueryTest extends Specification with TestWithDataStore {

  override val spec = "age:Int:index=true,name:String:index=true,dtg:Date,*geom:Point:srid=4326"

  val ff = CommonFactoryFinder.getFilterFactory2
  val geomBuilder = JTSFactoryFinder.getGeometryFactory
  val builder = new SimpleFeatureBuilder(sft, new AvroSimpleFeatureFactory)
  val data = List(
    ("1", Array(10, "johndoe", new Date), geomBuilder.createPoint(new Coordinate(10, 10))),
    ("2", Array(20, "janedoe", new Date), geomBuilder.createPoint(new Coordinate(20, 20))),
    ("3", Array(30, "johnrdoe", new Date), geomBuilder.createPoint(new Coordinate(20, 20)))
  )
  val features = data.map { case (id, attrs, geom) =>
    builder.reset()
    builder.addAll(attrs.asInstanceOf[Array[AnyRef]])
    val f = builder.buildFeature(id)
    f.setDefaultGeometry(geom)
    f.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
    f
  }

  addFeatures(features)

  "Id queries" should {
    "use record table to return a result" >> {
      val idQ = ff.id(ff.featureId("2"))
      val res = fs.getFeatures(idQ).features().toList
      res.length mustEqual 1
      res.head.getID mustEqual "2"
    }

    "handle multiple ids correctly" >> {
      val idQ = ff.id(ff.featureId("1"), ff.featureId("3"))
      val res = fs.getFeatures(idQ).features().toList
      res.length mustEqual 2
      res.map(_.getID) must contain ("1", "3")
    }

    "return no events when multiple IDs ANDed result in no intersection"  >> {
      val idQ1 = ff.id(ff.featureId("1"), ff.featureId("3"))
      val idQ2 = ff.id(ff.featureId("2"))
      val idQ =  ff.and(idQ1, idQ2)
      val qRes = fs.getFeatures(idQ)
      val res= qRes.features().toList
      res.length mustEqual 0
    }
  }
}
