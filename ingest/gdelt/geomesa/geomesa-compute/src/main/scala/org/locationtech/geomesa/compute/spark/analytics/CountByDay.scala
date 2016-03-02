/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.compute.spark.analytics

import java.text.SimpleDateFormat

import org.apache.hadoop.conf.Configuration
import org.apache.spark.{SparkConf, SparkContext}
import org.geotools.data.{DataStoreFinder, Query}
import org.geotools.factory.CommonFactoryFinder
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.geomesa.accumulo.data.AccumuloDataStore
import org.locationtech.geomesa.compute.spark.GeoMesaSpark

import scala.collection.JavaConversions._

object CountByDay {

  val params = Map(
    "instanceId" -> "mycloud",
    "zookeepers" -> "zoo1,zoo2,zoo3",
    "user"       -> "user",
    "password"   -> "password",
    "tableName"  -> "gdelt")

  val typeName = "event"
  val geom     = "geom"
  val date     = "SQLDATE"

  val bbox   = "-80, 35, -79, 36"
  val during = "2014-01-01T00:00:00.000Z/2014-01-31T12:00:00.000Z"

  val filter = s"bbox($geom, $bbox) AND $date during $during"

  def main(args: Array[String]) {
    // Get a handle to the data store
    val ds = DataStoreFinder.getDataStore(params).asInstanceOf[AccumuloDataStore]

    // Construct a CQL query to filter by bounding box
    val q = new Query(typeName, ECQL.toFilter(filter))

    // Configure Spark
    val sc = new SparkContext(GeoMesaSpark.init(new SparkConf(true), ds))

    // Create an RDD from a query
    val queryRDD = GeoMesaSpark.rdd(new Configuration, sc, params, q, None)

    // Convert RDD[SimpleFeature] to RDD[(String, SimpleFeature)] where the first
    // element of the tuple is the date to the day resolution
    val dayAndFeature = queryRDD.mapPartitions { iter =>
      val df = new SimpleDateFormat("yyyyMMdd")
      val ff = CommonFactoryFinder.getFilterFactory2
      val exp = ff.property(date)
      iter.map { f => (df.format(exp.evaluate(f).asInstanceOf[java.util.Date]), f) }
    }

    // Group the results by day
    val groupedByDay = dayAndFeature.groupBy { case (day, _) => day }

    // Count the number of features in each day
    val countByDay = groupedByDay.map { case (day, iter) => (day, iter.size) }

    // Collect the results and print
    countByDay.collect().foreach(println)
    println("\n")
  }

}
