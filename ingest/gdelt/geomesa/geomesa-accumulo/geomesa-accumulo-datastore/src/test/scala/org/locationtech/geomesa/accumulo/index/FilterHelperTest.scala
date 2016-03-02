/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.index

import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.geom.Coordinate
import org.geotools.factory.CommonFactoryFinder
import org.geotools.filter.text.ecql.ECQL
import org.geotools.geometry.jts.{JTSFactoryFinder, ReferencedEnvelope}
import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.filter.TestFilters._
import org.locationtech.geomesa.filter.FilterHelper
import org.locationtech.geomesa.filter.FilterHelper._
import org.locationtech.geomesa.utils.filters.Filters._
import org.opengis.filter.Filter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.util.Random

@RunWith(classOf[JUnitRunner])
class FilterHelperTest extends Specification with Mockito with LazyLogging {
  val ff = CommonFactoryFinder.getFilterFactory2
  val gf = JTSFactoryFinder.getGeometryFactory

  val min = IndexSchema.minDateTime
  val max = IndexSchema.maxDateTime
  val a   = new DateTime(2010,  1, 31, 23, 59, 59, DateTimeZone.forID("UTC"))
  val b   = new DateTime(2010,  3,  4, 10, 11, 12, DateTimeZone.forID("UTC"))
  val c   = new DateTime(2011,  2, 12, 15, 34, 23, DateTimeZone.forID("UTC"))
  val d   = new DateTime(2012, 11,  5,  5, 55, 11, DateTimeZone.forID("UTC"))

  val dts = Seq(a, b, c, d)
  val dtPairs: Seq[(DateTime, DateTime)] = dts.combinations(2).map(sortDates).toSeq
  val dtAndDtPairs = for( dt <- dts; dtPair <- dtPairs) yield (dt, dtPair)

  val dtFieldName = "dtg"
  val dtp = ff.property(dtFieldName)

  def fAfterDate(dt: DateTime): Filter = ff.after(dtp, dt2lit(dt))
  def fDateAfter(dt: DateTime): Filter = ff.after(dt2lit(dt), dtp)
  def fBeforeDate(dt: DateTime): Filter = ff.before(dtp, dt2lit(dt))
  def fDateBefore(dt: DateTime): Filter = ff.before(dt2lit(dt), dtp)

  def fLTDate(dt: DateTime): Filter = ff.less(dtp, dt2lit(dt))
  def fDateLT(dt: DateTime): Filter = ff.less(dt2lit(dt), dtp)
  def fGTDate(dt: DateTime): Filter = ff.greater(dtp, dt2lit(dt))
  def fDateGT(dt: DateTime): Filter = ff.greater(dt2lit(dt), dtp)
  def fLEDate(dt: DateTime): Filter = ff.lessOrEqual(dtp, dt2lit(dt))
  def fDateLE(dt: DateTime): Filter = ff.lessOrEqual(dt2lit(dt), dtp)
  def fGEDate(dt: DateTime): Filter = ff.greaterOrEqual(dtp, dt2lit(dt))
  def fDateGE(dt: DateTime): Filter = ff.greaterOrEqual(dt2lit(dt), dtp)

  def during(dt1: DateTime, dt2: DateTime): Filter = ff.during(dtp, dts2lit(dt1, dt2))
  def during(dtTuple: (DateTime, DateTime)): Filter = during(dtTuple._1, dtTuple._2)

  def between(dt1: DateTime, dt2: DateTime): Filter = ff.between(dtp, dt2lit(dt1), dt2lit(dt2))
  def between(dtTuple: (DateTime, DateTime)): Filter = between(dtTuple._1, dtTuple._2)

  def interval(dtTuple: (DateTime, DateTime)) = new Interval(dtTuple._1, dtTuple._2)
  def afterInterval(dt: DateTime): Interval   = new Interval(dt, max)
  def beforeInterval(dt: DateTime): Interval  = new Interval(min, dt)

  val extractDT: (Seq[Filter]) => Interval = extractInterval(_, Some(dtFieldName))

  def extractDateTime(fs: String): Interval = {
    val filter = ECQL.toFilter(fs)
    val filters = decomposeAnd(filter)
    extractDT(filters)
  }

  def sortDates(dates: Seq[DateTime]): (DateTime, DateTime) = {
    val sorted = dates.sortBy(_.getMillis)
    val start = sorted(0)
    val end = sorted(1)
    (start, end)
  }

  "extractTemporal " should {
    "return 0000 to date for all Before-date and date-After filters" in {
      forall(dts) { dt =>
        val expectedInterval = beforeInterval(dt)
        extractDT(Seq(fBeforeDate(dt))) must equalTo(expectedInterval)
        extractDT(Seq(fDateAfter(dt)))  must equalTo(expectedInterval)
        extractDT(Seq(fLTDate(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fLEDate(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fDateGT(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fDateGE(dt)))     must equalTo(expectedInterval)
      }
    }

    "return date to 9999 for After-date and date-Before filters" in {
      forall(dts) { dt =>
        val expectedInterval = afterInterval(dt)
        extractDT(Seq(fDateBefore(dt))) must equalTo(expectedInterval)
        extractDT(Seq(fAfterDate(dt)))  must equalTo(expectedInterval)
        extractDT(Seq(fDateLT(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fDateLE(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fGTDate(dt)))     must equalTo(expectedInterval)
        extractDT(Seq(fGEDate(dt)))     must equalTo(expectedInterval)
      }
    }

    "return date to 9999 for date-Before filters" in {
      forall(dts) { dt =>
        val extractedInterval = extractDT(Seq(fDateBefore(dt)))
        val expectedInterval = afterInterval(dt)
        extractedInterval must equalTo(expectedInterval)
      }
    }

    "return date1 to date2 for during filters" in {
      forall(dts.combinations(2).map(sortDates)) { case (start, end) =>

        val filter = during(start, end)

        val extractedInterval = extractDT(Seq(filter))
        val expectedInterval = new Interval(start, end)
        logger.debug(s"Extracted interval $extractedInterval from filter ${ECQL.toCQL(filter)}")
        extractedInterval must equalTo(expectedInterval)
      }
    }

    "offset dates for during filters" in {
      forall(dts.combinations(2).map(sortDates)) { case (start, end) =>
        val filter = during(start, end)
        val extractedInterval = extractInterval(Seq(filter), Some(dtFieldName), exclusive = true)
        val expectedInterval = new Interval(start.plusSeconds(1), end.minusSeconds(1))
        logger.debug(s"Extracted interval $extractedInterval from filter ${ECQL.toCQL(filter)}")
        extractedInterval must equalTo(expectedInterval)
      }
      val r = new Random(-7)
      forall(dts.combinations(2).map(sortDates)) { case (s, e) =>
        val start = s.plusMillis(r.nextInt(998) + 1)
        val end = e.plusMillis(r.nextInt(998) + 1)
        val filter = during(start, end)
        val extractedInterval = extractInterval(Seq(filter), Some(dtFieldName), exclusive = true)
        val expectedInterval = new Interval(s.plusSeconds(1), e)
        logger.debug(s"Extracted interval $extractedInterval from filter ${ECQL.toCQL(filter)}")
        extractedInterval must equalTo(expectedInterval)
      }
    }

    "return date1 to date2 for between filters" in {
      forall(dtPairs) { case (start, end) =>

        val filter = between(start, end)

        val extractedInterval = extractDT(Seq(filter))
        val expectedInterval = new Interval(start, end)
        logger.debug(s"Extracted interval $extractedInterval from filter ${ECQL.toCQL(filter)}")
        extractedInterval must equalTo(expectedInterval)
      }
    }

    "return appropriate interval for 'and'ed between/during filters" in {
      forall(dtPairs.combinations(2)) { dtTuples =>
        val t1 = dtTuples(0)
        val t2 = dtTuples(1)

        val betweenFilters = Seq(between(t1), between(t2))
        val duringFilters = Seq(during(t1), during(t2))
        val mixedFilters1 = Seq(during(t1), between(t2))
        val mixedFilters2 = Seq(between(t1), during(t2))

        val extractedBetweenInterval = extractDT(betweenFilters)
        val extractedDuringInterval = extractDT(duringFilters)
        val extractedMixed1Interval = extractDT(mixedFilters1)
        val extractedMixed2Interval = extractDT(mixedFilters2)

        val expectedInterval = interval(t1).overlap(interval(t2))
        logger.debug(s"Extracted interval $extractedBetweenInterval from filters ${betweenFilters.map(ECQL.toCQL)}")
        extractedBetweenInterval must equalTo(expectedInterval)
        extractedDuringInterval must equalTo(expectedInterval)
        extractedMixed1Interval must equalTo(expectedInterval)
        extractedMixed2Interval must equalTo(expectedInterval)
      }
    }

    "return appropriate interval for 'and's of before/after and between/during filters" in {
      forall(dtAndDtPairs) { case (dt, dtPair) =>
        val afterDtFilter = fAfterDate(dt)
        val beforeDtFilter = fBeforeDate(dt)

        val afterDtInterval = afterInterval(dt)
        val beforeDtInterval = beforeInterval(dt)

        val betweenFilter = between(dtPair)
        val duringFilter = during(dtPair)
        val pairInterval = interval(dtPair)

        val afterAndBetween = extractDT(Seq(afterDtFilter, betweenFilter))
        val afterAndBetweenInterval = afterDtInterval.overlap(pairInterval)
        afterAndBetween must equalTo(afterAndBetweenInterval)

        val beforeAndBetween = extractDT(Seq(beforeDtFilter, betweenFilter))
        val beforeAndBetweenInterval = beforeDtInterval.overlap(pairInterval)
        beforeAndBetween must equalTo(beforeAndBetweenInterval)

        val afterAndDuring = extractDT(Seq(afterDtFilter, duringFilter))
        val afterAndDuringInterval = afterDtInterval.overlap(pairInterval)
        afterAndDuring must equalTo(afterAndDuringInterval)

        val beforeAndDuring = extractDT(Seq(beforeDtFilter, duringFilter))
        val beforeAndDuringInterval = beforeDtInterval.overlap(pairInterval)
        beforeAndDuring must equalTo(beforeAndDuringInterval)
      }
    }
  }

  "filterListAsAnd as an inverse of decomposeAnd" should {
    "handle empty sequences" in {
     val emptyFilterSeq = Seq[Filter]()
     val filteredSeq = filterListAsAnd(emptyFilterSeq)

      filteredSeq.isDefined must beFalse
    }

    "handle sequences with just one entry" in {
      val processed = baseFilters.flatMap{filter => filterListAsAnd(decomposeAnd(filter))}
      val difference = processed diff baseFilters

      difference.isEmpty must beTrue
    }
  }

  "findBest" should {

    val a = mockAs[Filter]("a")
    val b = mockAs[Filter]("b")
    val c = mockAs[Filter]("c")
    val d = mockAs[Filter]("d")

    "return none when the sequence is empty" >> {
      val cost = (f: Filter) => Some(0L)
      val filters = Seq.empty[Filter]

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[UnknownCost] must beTrue
      result.bestFilter must beNone
      result.otherFilters mustEqual filters
    }

    "return none when no costs are known" >> {
      val cost = (f: Filter) => None : Option[Long]
      val filters = Seq(a, b, c)

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[UnknownCost] must beTrue
      result.bestFilter must beNone
      result.otherFilters mustEqual filters
    }

    "return best if first" >> {
      val lookup = Seq(
        (a, Some(1L)),
        (b, Some(2L)),
        (c, None),
        (d, Some(3L))
      )

      val filters = lookup.map(_._1)
      val cost: (Filter) => Option[Long] = lookup.toMap

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[KnownCost] must beTrue
      result.bestFilter must beSome(a)
      result.otherFilters mustEqual Seq(b, c, d)
      result.asInstanceOf[KnownCost].cost mustEqual 1
    }

    "return best if after unknown" >> {
      val lookup = Seq(
        (a, None),
        (b, Some(2L)),
        (c, Some(6L)),
        (d, Some(4L))
      )

      val filters = lookup.map(_._1)
      val cost: (Filter) => Option[Long] = lookup.toMap

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[KnownCost] must beTrue
      result.bestFilter must beSome(b)
      result.otherFilters mustEqual Seq(a, c, d)
      result.asInstanceOf[KnownCost].cost mustEqual 2
    }

    "return best if after worse" >> {
      val lookup = Seq(
        (a, Some(5L)),
        (b, None),
        (c, Some(3L)),
        (d, Some(5L))
      )

      val filters = lookup.map(_._1)
      val cost: (Filter) => Option[Long] = lookup.toMap

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[KnownCost] must beTrue
      result.bestFilter must beSome(c)
      result.otherFilters mustEqual Seq(a, b, d)
      result.asInstanceOf[KnownCost].cost mustEqual 3
    }

    "return best if last" >> {
      val lookup = Seq(
        (a, Some(30L)),
        (b, None),
        (c, Some(20L)),
        (d, Some(10L))
      )

      val filters = lookup.map(_._1)
      val cost: (Filter) => Option[Long] = lookup.toMap

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[KnownCost] must beTrue
      result.bestFilter must beSome(d)
      result.otherFilters mustEqual Seq(a, b, c)
      result.asInstanceOf[KnownCost].cost mustEqual 10
    }

    "return first best if multiple" >> {
      val lookup = Seq(
        (a, Some(200L)),
        (b, Some(5L)),
        (c, None),
        (d, Some(5L))
      )

      val filters = lookup.map(_._1)
      val cost: (Filter) => Option[Long] = lookup.toMap

      val result = FilterHelper.findBest(cost)(filters)

      result.isInstanceOf[KnownCost] must beTrue
      result.bestFilter must beSome(b)
      result.otherFilters mustEqual Seq(a, c, d)
      result.asInstanceOf[KnownCost].cost mustEqual 5
    }
  }

  "tryMergeGeoms" should {
    "intersect BBOX geoms" >> {
      implicit def tupleToCoord(t: (Int, Int)): Coordinate = new Coordinate(t._1, t._2)
      val one = gf.createPolygon(Seq[Coordinate]((1, 1), (4, 1), (4, 4), (1, 4), (1, 1)).toArray)
      val two = gf.createPolygon(Seq[Coordinate]((1, 1), (2, 1), (2, 2), (1, 2), (1, 1)).toArray)
      val f1 = ff.bbox(ff.property("geom"), ReferencedEnvelope.reference(one.getEnvelopeInternal))
      val f2 = ff.bbox(ff.property("geom"), ReferencedEnvelope.reference(two.getEnvelopeInternal))
      FilterHelper.tryReduceGeometryFilter(Seq(f1, f2)).head must be equalTo(f2)
    }
  }
}
