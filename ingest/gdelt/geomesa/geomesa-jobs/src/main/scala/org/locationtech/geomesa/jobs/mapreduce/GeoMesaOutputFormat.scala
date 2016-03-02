/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.jobs.mapreduce

import java.io.IOException

import com.typesafe.scalalogging.LazyLogging
import org.apache.accumulo.core.client.BatchWriterConfig
import org.apache.accumulo.core.client.mapreduce.AccumuloOutputFormat
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.data.Mutation
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce._
import org.geotools.data.{DataStoreFinder, DataUtilities}
import org.locationtech.geomesa.accumulo.data.AccumuloFeatureWriter.{FeatureToMutations, FeatureToWrite}
import org.locationtech.geomesa.accumulo.data.{AccumuloDataStore, AccumuloDataStoreFactory, AccumuloDataStoreParams, AccumuloFeatureWriter}
import org.locationtech.geomesa.accumulo.index.IndexValueEncoder
import org.locationtech.geomesa.features.SimpleFeatureSerializers
import org.locationtech.geomesa.jobs.GeoMesaConfigurator
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.collection.JavaConversions._

object GeoMesaOutputFormat {

  object Counters {
    val Group   = "org.locationtech.geomesa.jobs.output"
    val Written = "written"
    val Failed  = "failed"
  }

  /**
   * Configure the data store you will be writing to.
   */
  def configureDataStore(job: Job, dsParams: Map[String, String]): Unit = {

    val ds = DataStoreFinder.getDataStore(dsParams).asInstanceOf[AccumuloDataStore]

    assert(ds != null, "Invalid data store parameters")

    // set up the underlying accumulo input format
    val user = AccumuloDataStoreParams.userParam.lookUp(dsParams).asInstanceOf[String]
    val password = AccumuloDataStoreParams.passwordParam.lookUp(dsParams).asInstanceOf[String]
    AccumuloOutputFormat.setConnectorInfo(job, user, new PasswordToken(password.getBytes))

    val instance = AccumuloDataStoreParams.instanceIdParam.lookUp(dsParams).asInstanceOf[String]
    val zookeepers = AccumuloDataStoreParams.zookeepersParam.lookUp(dsParams).asInstanceOf[String]
    AccumuloOutputFormat.setZooKeeperInstance(job, instance, zookeepers)

    AccumuloOutputFormat.setCreateTables(job, false)

    // also set the datastore parameters so we can access them later
    val conf = job.getConfiguration
    GeoMesaConfigurator.setDataStoreOutParams(conf, dsParams)
    GeoMesaConfigurator.setSerialization(conf)
  }

  /**
   * Configure the batch writer options used by accumulo.
   */
  def configureBatchWriter(job: Job, writerConfig: BatchWriterConfig): Unit =
    AccumuloOutputFormat.setBatchWriterOptions(job, writerConfig)
}

/**
 * Output format that turns simple features into mutations and delegates to AccumuloOutputFormat
 */
class GeoMesaOutputFormat extends OutputFormat[Text, SimpleFeature] {

  val delegate = new AccumuloOutputFormat

  override def getRecordWriter(context: TaskAttemptContext) = {
    val params = GeoMesaConfigurator.getDataStoreOutParams(context.getConfiguration)
    new GeoMesaRecordWriter(params, context, delegate.getRecordWriter(context))
  }

  override def checkOutputSpecs(context: JobContext) = {
    val params = GeoMesaConfigurator.getDataStoreOutParams(context.getConfiguration)
    if (!AccumuloDataStoreFactory.canProcess(params)) {
      throw new IOException("Data store connection parameters are not set")
    }
    delegate.checkOutputSpecs(context)
  }

  override def getOutputCommitter(context: TaskAttemptContext) = delegate.getOutputCommitter(context)
}

/**
 * Record writer for GeoMesa SimpleFeatures.
 *
 * Key is ignored. If the feature type for the given feature does not exist yet, it will be created.
 */
class GeoMesaRecordWriter(params: Map[String, String],
                          context: TaskAttemptContext,
                          delegate: RecordWriter[Text, Mutation])
    extends RecordWriter[Text, SimpleFeature] with LazyLogging {

  type TableAndMutations = (Text, FeatureToMutations)

  val ds = DataStoreFinder.getDataStore(params).asInstanceOf[AccumuloDataStore]

  val sftCache          = scala.collection.mutable.Map.empty[String, SimpleFeatureType]
  val writerCache       = scala.collection.mutable.Map.empty[String, Seq[TableAndMutations]]
  val encoderCache      = scala.collection.mutable.Map.empty[String, org.locationtech.geomesa.features.SimpleFeatureSerializer]
  val indexEncoderCache = scala.collection.mutable.Map.empty[String, IndexValueEncoder]

  val written = context.getCounter(GeoMesaOutputFormat.Counters.Group, GeoMesaOutputFormat.Counters.Written)
  val failed  = context.getCounter(GeoMesaOutputFormat.Counters.Group, GeoMesaOutputFormat.Counters.Failed)

  override def write(key: Text, value: SimpleFeature) = {
    val sftName = value.getFeatureType.getTypeName
    // TODO we shouldn't serialize the sft with each feature
    // ensure that the type has been created if we haven't seen it before
    val sft = sftCache.getOrElseUpdate(sftName, {
      // schema operations are thread-safe
      val existing = ds.getSchema(sftName)
      if (existing == null) {
        ds.createSchema(value.getFeatureType)
        ds.getSchema(sftName)
      } else {
        existing
      }
    })

    val writers = writerCache.getOrElseUpdate(sftName, {
      AccumuloFeatureWriter.getTablesAndWriters(sft, ds).map {
        case (table, writer) => (new Text(table), writer)
      }
    })

    val withFid = AccumuloFeatureWriter.featureWithFid(sft, value)
    val encoder = encoderCache.getOrElseUpdate(sftName, SimpleFeatureSerializers(sft, ds.getFeatureEncoding(sft)))
    val ive = indexEncoderCache.getOrElseUpdate(sftName, IndexValueEncoder(sft))
    val featureToWrite = new FeatureToWrite(withFid, ds.writeVisibilities, encoder, ive)

    // calculate all the mutations first, so that if something fails we won't have a partially written feature
    try {
      val mutations = writers.map { case (table, featToMuts) => (table, featToMuts(featureToWrite)) }
      mutations.foreach { case (table, muts) => muts.foreach(delegate.write(table, _)) }
      written.increment(1)
    } catch {
      case e: Exception =>
        logger.error(s"Error creating mutations from feature '${DataUtilities.encodeFeature(withFid)}'", e)
        failed.increment(1)
    }
  }

  override def close(context: TaskAttemptContext) = delegate.close(context)
}