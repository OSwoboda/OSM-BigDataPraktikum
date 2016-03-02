/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/


package org.locationtech.geomesa.compute.spark

import java.io.{Serializable => JSerializable}
import java.util.{Properties, UUID}

import com.typesafe.scalalogging.LazyLogging
import org.apache.accumulo.core.client.mock.MockInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.security.Authorizations
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.Text
import org.apache.spark.{SparkConf, SparkContext}
import org.geotools.data.collection.ListFeatureCollection
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.data.{DataStore, DataStoreFinder, DataUtilities, Query}
import org.geotools.factory.Hints
import org.joda.time.{DateTime, DateTimeZone}
import org.junit
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.data.{AccumuloDataStore, AccumuloDataStoreFactory, AccumuloFeatureStore}
import org.locationtech.geomesa.accumulo.index.Constants
import org.locationtech.geomesa.features.ScalaSimpleFeatureFactory
import org.locationtech.geomesa.security.SecurityUtils
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.locationtech.geomesa.utils.text.WKTUtils
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class GeoMesaSparkTest extends Specification with LazyLogging {

  sequential

  var sc: SparkContext = null

  @junit.After
  def shutdown(): Unit = {
    Option(sc).foreach(_.stop())
  }

  "GeoMesaSpark" should {
    val testData: Map[String, String] = {
      val dataFile = new Properties
      dataFile.load(getClass.getClassLoader.getResourceAsStream("polygons.properties"))
      dataFile.toMap
    }

    val TEST_TABLE_NAME = "geomesa_spark_test"

    import org.locationtech.geomesa.accumulo.data.AccumuloDataStoreParams._
    lazy val dsParams = Map[String, String](
      zookeepersParam.key -> "dummy",
      instanceIdParam.key -> "dummy",
      userParam.key -> "user",
      passwordParam.key -> "pass",
      tableNameParam.key -> TEST_TABLE_NAME,
      mockParam.key -> "true")

    lazy val ds: DataStore = {
      val mockInstance = new MockInstance("dummy")
      val c = mockInstance.getConnector("user", new PasswordToken("pass".getBytes))
      c.tableOperations.create(TEST_TABLE_NAME)
      val splits = (0 to 99).map(s => "%02d".format(s)).map(new Text(_))
      c.tableOperations().addSplits(TEST_TABLE_NAME, new java.util.TreeSet[Text](splits.asJava))

      val dsf = new AccumuloDataStoreFactory

      val ds = dsf.createDataStore(dsParams.mapValues(_.asInstanceOf[JSerializable]).asJava)
      ds
    }

    lazy val spec = "id:Integer,map:Map[String,Integer],dtg:Date,geom:Geometry:srid=4326"

    def createFeatures(ds: DataStore, sft: SimpleFeatureType, encodedFeatures: Array[_ <: Array[_]]): Seq[SimpleFeature] = {
      val builder = ScalaSimpleFeatureFactory.featureBuilder(sft)
      val features = encodedFeatures.map {
        e =>
          val f = builder.buildFeature(e(0).toString, e.asInstanceOf[Array[AnyRef]])
          f.getUserData.put(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
          f.getUserData.put(Hints.PROVIDED_FID, e(0).toString)
          f
      }
      features
    }

    def createTypeName() = s"sparktest${UUID.randomUUID().toString}"
    def createSFT(typeName: String) = {
      val t = SimpleFeatureTypes.createType(typeName, spec)
      t.getUserData.put(Constants.SF_PROPERTY_START_TIME, "dtg")
      t
    }

    val random = new Random(83)
    val encodedFeatures = (0 until 150).toArray.map {
      i =>
        Array(
          i.toString,
          Map("a" -> i, "b" -> i * 2, (if (random.nextBoolean()) "c" else "d") -> random.nextInt(10)).asJava,
          new DateTime("2012-01-01T19:00:00", DateTimeZone.UTC).toDate,
          "POINT(-77 38)")
    }

    "Read data" in {
      val typeName = s"sparktest${UUID.randomUUID().toString}"
      val sft = createSFT(typeName)

      ds.createSchema(sft)
      ds.getSchema(typeName) should not(beNull)
      val fs = ds.getFeatureSource(typeName).asInstanceOf[SimpleFeatureStore]
      val feats = createFeatures(ds, sft, encodedFeatures)
      fs.addFeatures(DataUtilities.collection(feats.asJava))
      fs.getTransaction.commit()

      val conf = new SparkConf().setMaster("local[2]").setAppName("testSpark")
      GeoMesaSpark.init(conf, ds)
      Option(sc).foreach(_.stop())
      sc = new SparkContext(conf) // will get shut down by shutdown method

      val rdd = GeoMesaSpark.rdd(new Configuration(), sc, dsParams, new Query(typeName), useMock = true)

      rdd.count() should equalTo(feats.length)
      feats.map(_.getAttribute("id")) should contain(rdd.take(1).head.getAttribute("id"))
    }

    "Write data" in {
      val typeName = s"sparktest${UUID.randomUUID().toString}"
      val sft = createSFT(typeName)
      ds.createSchema(sft)

      val conf = new SparkConf().setMaster("local[2]").setAppName("testSpark")
      GeoMesaSpark.init(conf, ds)
      Option(sc).foreach(_.stop())
      sc = new SparkContext(conf) // will get shut down by shutdown method
      val feats = createFeatures(ds, sft, encodedFeatures)

      val rdd = sc.makeRDD(feats)

      GeoMesaSpark.save(rdd, dsParams, typeName)

      val coll = ds.getFeatureSource(typeName).getFeatures
      coll.size() should equalTo(encodedFeatures.length)
      feats.map(_.getAttribute("id")) should contain(coll.features().next().getAttribute("id"))
    }
  }

  "GeoMesaSpark with auths" should {

    def getFeatures(sft: SimpleFeatureType) = (0 until 6).map { i =>
      val builder = ScalaSimpleFeatureFactory.featureBuilder(sft)
      builder.set("geom", WKTUtils.read("POINT(45.0 45.0)"))
      builder.set("dtg", "2012-01-02T05:06:07.000Z")
      builder.set("name",i.toString)
      val sf = builder.buildFeature(i.toString)
      sf.getUserData()(Hints.USE_PROVIDED_FID) = java.lang.Boolean.TRUE
      sf
    }

    "read data with authorizations" in {
      val instanceName = "sparkAuthsInstance"
      val mockInstance = new MockInstance(instanceName)
      val conn = mockInstance.getConnector("myuser", new PasswordToken("mypassword".getBytes("UTF8")))
      conn.securityOperations().changeUserAuthorizations("myuser", new Authorizations("user", "admin"))
      conn.securityOperations().createLocalUser("nonpriv", new PasswordToken("nonpriv".getBytes("UTF8")))
      conn.securityOperations().changeUserAuthorizations("nonpriv", new Authorizations("user"))

      // create the data store
      val privParams = Map(
        "instanceId"        -> instanceName,
        "zookeepers"        -> "zoo1:2181,zoo2:2181,zoo3:2181",
        "user"              -> "myuser",
        "password"          -> "mypassword",
        "tableName"         -> "testwrite",
        "useMock"           -> "true",
        "auths"             -> "user,admin")
      val privDS = DataStoreFinder.getDataStore(privParams).asInstanceOf[AccumuloDataStore]

      val sftName = "sparkAuthTest"
      val sft = SimpleFeatureTypes.createType(sftName, s"name:String,dtg:Date,*geom:Point:srid=4326")
      sft.setDtgField("dtg")
      privDS.createSchema(sft)

      // write some data
      val fs = privDS.getFeatureSource(sftName).asInstanceOf[AccumuloFeatureStore]

      val features = getFeatures(sft).toList
      val privFeatures = features.take(3)
      privFeatures.foreach { f => f.getUserData.put(SecurityUtils.FEATURE_VISIBILITY, "user&admin") }

      val nonPrivFeatures = features.drop(3)
      nonPrivFeatures.foreach { f => f.getUserData.put(SecurityUtils.FEATURE_VISIBILITY, "user") }

      fs.addFeatures(new ListFeatureCollection(sft, privFeatures ++ nonPrivFeatures))
      fs.flush()

      "user&admin should get 6 features" >> {
        val conf = new SparkConf().setMaster("local[2]").setAppName("testSpark")
        GeoMesaSpark.init(conf, privDS)
        Option(sc).foreach(_.stop())
        sc =  new SparkContext(conf) // will get shut down by shutdown method

        val rdd = GeoMesaSpark.rdd(new Configuration(), sc, privParams, new Query(sftName), useMock = true)
        rdd.count() mustEqual 6
        features.map(_.getAttribute("id")) should contain(rdd.take(1).head.getAttribute("id"))
      }

      "user should get 3" >> {
        val conf = new SparkConf().setMaster("local[2]").setAppName("testSpark")
        GeoMesaSpark.init(conf, privDS)
        Option(sc).foreach(_.stop())
        sc =  new SparkContext(conf) // will get shut down by shutdown method

        val rdd = GeoMesaSpark.rdd(new Configuration(), sc, privParams.updated("auths", "user"), new Query(sftName), useMock = true)
        rdd.count() mustEqual 3
      }

      "no auths gets zero" >> {
        val conf = new SparkConf().setMaster("local[2]").setAppName("testSpark")
        GeoMesaSpark.init(conf, privDS)
        Option(sc).foreach(_.stop())
        sc =  new SparkContext(conf) // will get shut down by shutdown method

        val rdd = GeoMesaSpark.rdd(new Configuration(), sc, privParams - "auths", new Query(sftName), useMock = true)
        rdd.count() mustEqual 0
      }
    }
  }

}
