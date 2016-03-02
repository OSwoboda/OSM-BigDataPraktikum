/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo

import org.apache.accumulo.core.data.{Key, Value}
import org.apache.hadoop.io.Text
import org.geotools.data.FeatureWriter
import org.geotools.factory.Hints.ClassKey
import org.joda.time.{DateTime, Interval}
import org.locationtech.geomesa.features.SerializationType
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

package object data {

  // Datastore parameters
  val INSTANCE_ID      = "geomesa.instance.id"
  val ZOOKEEPERS       = "geomesa.zookeepers"
  val ACCUMULO_USER    = "geomesa.user"
  val ACCUMULO_PASS    = "geomesa.pass"
  val AUTHS            = "geomesa.auths"
  val AUTH_PROVIDER    = "geomesa.auth.provider"
  val VISIBILITY       = "geomesa.visibility"
  val TABLE            = "geomesa.table"
  val FEATURE_NAME     = "geomesa.feature.name"
  val FEATURE_ENCODING = "geomesa.feature.encoding"

  // Metadata keys
  val ATTRIBUTES_KEY         = "attributes"
  val SPATIAL_BOUNDS_KEY     = "bounds"
  val TEMPORAL_BOUNDS_KEY    = "time.bounds"
  val SCHEMA_KEY             = "schema"
  val DTGFIELD_KEY           = "dtgfield"
  val FEATURE_ENCODING_KEY   = "featureEncoding"
  val VISIBILITIES_KEY       = "visibilities"
  val VISIBILITIES_CHECK_KEY = "visibilitiesCheck"
  val ST_IDX_TABLE_KEY       = "tables.idx.st.name"
  val ATTR_IDX_TABLE_KEY     = "tables.idx.attr.name"
  val RECORD_TABLE_KEY       = "tables.record.name"
  val Z3_TABLE_KEY           = "tables.z3.name"
  val QUERIES_TABLE_KEY      = "tables.queries.name"
  val SHARED_TABLES_KEY      = "tables.sharing"
  val TABLES_ENABLED_KEY     = SimpleFeatureTypes.ENABLED_INDEXES
  val SCHEMA_ID_KEY          = "id"
  val VERSION_KEY            = "version"

  // Storage implementation constants
  val DATA_CQ              = new Text("SimpleFeatureAttribute")
  val SFT_CF               = new Text("SFT")
  val METADATA_TAG         = "~METADATA"
  val METADATA_TAG_END     = s"$METADATA_TAG~~"
  val EMPTY_STRING         = ""
  val EMPTY_VALUE          = new Value(Array[Byte]())
  val EMPTY_COLF           = new Text(EMPTY_STRING)
  val EMPTY_COLQ           = new Text(EMPTY_STRING)
  val EMPTY_VIZ            = new Text(EMPTY_STRING)
  val EMPTY_TEXT           = new Text()
  val WHOLE_WORLD_BOUNDS   = "-180.0:180.0:-90.0:90.0"
  val ALL_TIME_BOUNDS      = new Interval(new DateTime(0l), new DateTime())  // Epoch till now
  val DEFAULT_ENCODING     = SerializationType.KRYO

  // SimpleFeature Hints
  val TRANSFORMS           = new ClassKey(classOf[String])
  val TRANSFORM_SCHEMA     = new ClassKey(classOf[SimpleFeatureType])
  val GEOMESA_UNIQUE       = new ClassKey(classOf[String])

  type SFFeatureWriter = FeatureWriter[SimpleFeatureType, SimpleFeature]
}
