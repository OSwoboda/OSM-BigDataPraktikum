/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.data

import java.util.Date

import org.geotools.data.Query
import org.geotools.factory.{CommonFactoryFinder, Hints}
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.filter.text.ecql.ECQL
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.TestWithDataStore
import org.locationtech.geomesa.utils.geotools.Conversions._
import org.locationtech.geomesa.utils.text.WKTUtils
import org.opengis.filter.Filter
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ComplexFeatureTest extends Specification with TestWithDataStore {

  // Due to the implementation of mock connectors, this test needs to be sequential.
  // Our kryo decoding of the accumulo key/value pairs actually mutates the byte array as it
  // decodes. The array is returned to it's original state after decoding - however, the mock
  // connector returns the same byte array object every time for a given accumulo value. This causes
  // serialization errors if we run the test non-sequentially.
  sequential

  override def spec =
    """
      |names:List[String],
      |fingers:List[String],
      |skills:Map[String,Integer],
      |metadata:Map[Double,String],
      |dtg:Date,
      |*geom:Point:srid=4326
    """.stripMargin

  addFeatures({
    // create and add a feature
    val builder = new SimpleFeatureBuilder(sft, CommonFactoryFinder.getFeatureFactory(null))
    builder.addAll(List(
      List("joe", "joseph"),
      List("pointer", "thumb", "ring").asJava,
      Map("java" -> 1, "scala" -> 100),
      Map(1.0 -> "value1", 2.0 -> "value2").asJava,
      new Date(),
      WKTUtils.read("POINT(45.0 49.0)")
    ).asJava)
    val liveFeature = builder.buildFeature("fid-1")
    liveFeature.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
    Seq(liveFeature)
  })

  "SimpleFeatures" should {

    "set and return list values" >> {
      val query = new Query(sftName, Filter.INCLUDE)
      val features = fs.getFeatures(query).features().toList
      features must haveSize(1)

      "in java" >> {
        val fingers = features(0).getAttribute("fingers")
        fingers must not beNull;
        fingers must beAnInstanceOf[java.util.List[_]]
        val fingersList = fingers.asInstanceOf[java.util.List[String]].asScala
        fingersList must haveSize(3)
        fingersList must contain("pointer", "thumb", "ring")
      }
      "in scala" >> {
        val names = features(0).getAttribute("names")
        names must not beNull;
        names must beAnInstanceOf[java.util.List[_]]
        val namesList = names.asInstanceOf[java.util.List[String]].asScala
        namesList must haveSize(2)
        namesList must contain("joe", "joseph")
      }
    }

    "set and return map values" >> {
      val query = new Query(sftName, Filter.INCLUDE)
      val features = fs.getFeatures(query).features().toList
      features must haveSize(1)

      "in java" >> {
        val metadata = features(0).getAttribute("metadata")
        metadata must not beNull;
        metadata must beAnInstanceOf[java.util.Map[_, _]]
        val metadataMap = metadata.asInstanceOf[java.util.Map[Double, String]].asScala
        metadataMap must haveSize(2)
        metadataMap must havePairs(1.0 -> "value1", 2.0 -> "value2")
      }
      "in scala" >> {
        val skills = features(0).getAttribute("skills")
        skills must not beNull;
        skills must beAnInstanceOf[java.util.Map[_, _]]
        val skillsMap = skills.asInstanceOf[java.util.Map[String, Int]].asScala
        skillsMap must haveSize(2)
        skillsMap must havePairs("java" -> 1, "scala" -> 100)
      }
    }

    "query on list items" >> {
      "for single entries in the list" >> {
        val query = new Query(sftName, ECQL.toFilter("names = 'joe'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(1)
      }
      "for multiple entries in the list" >> {
        val query = new Query(sftName, ECQL.toFilter("fingers = 'thumb' AND fingers = 'ring'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(1)
      }
      "for ranges" >> {
        val query = new Query(sftName, ECQL.toFilter("names > 'jane'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(1)
      }
      "not match invalid filters" >> {
        val query = new Query(sftName, ECQL.toFilter("fingers = 'index'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(0)
      }
      "not match invalid ranges" >> {
        val query = new Query(sftName, ECQL.toFilter("names < 'jane'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(0)
      }
    }

    "query on map items" >> {
      "for keys in the map" >> {
        val query = new Query(sftName, ECQL.toFilter("skills = 'java'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(1)
      }.pendingUntilFixed("GEOMESA-454 - can't query on maps")

      "for values in the map" >> {
        val query = new Query(sftName, ECQL.toFilter("skills = '100'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(1)
      }.pendingUntilFixed("GEOMESA-454 - can't query on maps")

      "not match invalid filters" >> {
        val query = new Query(sftName, ECQL.toFilter("skills = 'fortran'"))
        val features = fs.getFeatures(query).features().toList
        features must haveSize(0)
      }
    }
  }

}
