/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.stats

import org.apache.accumulo.core.client.mock.MockInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.security.Authorizations
import org.geotools.data.Query
import org.geotools.filter.text.cql2.CQL
import org.geotools.geometry.jts.ReferencedEnvelope
import org.joda.time.format.DateTimeFormat
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.index.QueryHints
import org.locationtech.geomesa.accumulo.util.GeoMesaBatchWriterConfig
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class QueryStatTransformTest extends Specification {

  val df = DateTimeFormat.forPattern("yyyy.MM.dd HH:mm:ss")

  val table = "QueryStatTransformTest"
  val featureName = "stat-writer-test"

  val connector = new MockInstance().getConnector("user", new PasswordToken("password"))
  connector.tableOperations().create(table)

  "QueryStatTransform" should {

    "convert query stats to and from accumulo" in {

      // currently we don't restore table and feature in the query stat - thus setting them null here
      val stat = QueryStat(featureName, 500L, "user1", "attr=1", "hint1=true", 101L, 201L, 11)

      val writer = connector.createBatchWriter(table, GeoMesaBatchWriterConfig())

      writer.addMutation(QueryStatTransform.statToMutation(stat))
      writer.flush()
      writer.close()

      val scanner = connector.createScanner(table, new Authorizations())

      val converted = QueryStatTransform.rowToStat(scanner.iterator().asScala.toList)

      converted mustEqual stat
    }

    "convert hints to readable string" in {

      val query = new Query("test", CQL.toFilter("INCLUDE"))
      val env = new ReferencedEnvelope()
      query.getHints.put(QueryHints.DENSITY_BBOX_KEY, env)
      query.getHints.put(QueryHints.WIDTH_KEY, 500)
      query.getHints.put(QueryHints.HEIGHT_KEY, 500)

      val hints = QueryStatTransform.hintsToString(query.getHints)

      hints must contain(s"DENSITY_BBOX_KEY=$env")
      hints must contain("WIDTH_KEY=500")
      hints must contain("HEIGHT_KEY=500")
    }
  }
}
