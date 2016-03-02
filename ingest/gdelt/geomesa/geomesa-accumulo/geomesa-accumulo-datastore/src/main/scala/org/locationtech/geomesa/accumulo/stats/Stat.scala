/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.stats

import java.util.Map.Entry

import com.typesafe.scalalogging.LazyLogging
import org.apache.accumulo.core.client.Scanner
import org.apache.accumulo.core.data.{Key, Mutation, Value}
import org.joda.time.format.DateTimeFormat
import org.locationtech.geomesa.accumulo.util.{CloseableIterator, SelfClosingIterator}

import scala.util.Random

/**
 * Base trait for all stat types
 */
trait Stat {
  def typeName: String
  def date: Long
}

/**
 * Trait for mapping stats to accumulo and back
 */
trait StatTransform[S <: Stat] extends LazyLogging {

  protected def createMutation(stat: Stat) = new Mutation(s"${stat.typeName}~${StatTransform.dateFormat.print(stat.date)}")

  protected def createRandomColumnFamily = Random.nextInt(9999).formatted("%1$04d")

  /**
   * Convert a stat to a mutation
   *
   * @param stat
   * @return
   */
  def statToMutation(stat: S): Mutation

  /**
   * Convert accumulo scan results into a stat
   *
   * @param entries
   * @return
   */
  def rowToStat(entries: Iterable[Entry[Key, Value]]): S

  /**
   * Creates an iterator that returns Stats from accumulo scans
   *
   * @param scanner
   * @return
   */
  def iterator(scanner: Scanner): CloseableIterator[S] = {
    val iter = scanner.iterator()

    val wrappedIter = new CloseableIterator[S] {

      var last: Option[Entry[Key, Value]] = None

      override def close() = scanner.close()

      override def next() = {
        // get the data for the stat entry, which consists of a several CQ/values
        val entries = collection.mutable.ListBuffer.empty[Entry[Key, Value]]
        if (last.isEmpty) {
          last = Some(iter.next())
        }
        val lastRowKey = last.get.getKey.getRow.toString
        var next: Option[Entry[Key, Value]] = last
        while (next.isDefined && next.get.getKey.getRow.toString == lastRowKey) {
          entries.append(next.get)
          next = if (iter.hasNext) Some(iter.next()) else None
        }
        last = next
        // use the row data to return a Stat
        rowToStat(entries)
      }

      override def hasNext = last.isDefined || iter.hasNext
    }
    SelfClosingIterator(wrappedIter)
  }
}

object StatTransform {
  val dateFormat = DateTimeFormat.forPattern("yyyyMMdd-HH:mm:ss.SSS").withZoneUTC()
}
