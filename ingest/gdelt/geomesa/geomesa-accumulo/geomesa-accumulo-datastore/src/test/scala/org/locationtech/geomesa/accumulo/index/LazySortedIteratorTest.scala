/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.accumulo.index

import java.util.NoSuchElementException

import org.geotools.factory.CommonFactoryFinder
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.junit.runner.RunWith
import org.locationtech.geomesa.accumulo.util.CloseableIterator
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.opengis.filter.sort.{SortBy, SortOrder}
import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LazySortedIteratorTest extends Specification with Mockito {

  val ff = CommonFactoryFinder.getFilterFactory

  "LazySortedIterator" should {

    val sft = SimpleFeatureTypes.createType("ns:test", "age:Int,name:String,foo:Int")
    val builder = new SimpleFeatureBuilder(sft)

    def buildSF(id: Int, name: String, age: Int): SimpleFeature = {
      import scala.collection.JavaConversions._
      builder.reset()
      builder.addAll(List[AnyRef](age : java.lang.Integer, name))
      builder.buildFeature(id.toString)
    }
    val a =  buildSF(1, "A", 7)
    val b =  buildSF(2, "B", 9)
    val c1 = buildSF(3, "C", 6)
    val c2 = buildSF(4, "C", 9)
    val d =  buildSF(5, "D", 6)

    "lazily sort" >> {

      "when hasNext is called first" >> {
        val features = mock[CloseableIterator[SimpleFeature]]
        features.hasNext returns true thenReturns true thenReturns false
        features.next returns b thenReturns a thenThrows new NoSuchElementException

        val test = new LazySortedIterator(features, sft, Array(SortBy.NATURAL_ORDER))

        there was no(features).hasNext
        there was no(features).next
        there was no(features).close

        test.hasNext must beTrue

        there were three(features).hasNext
        there were two(features).next()
        there were one(features).close()
      }

      "or when next is called first" >> {
        val features = mock[CloseableIterator[SimpleFeature]]
        features.hasNext returns true thenReturns true thenReturns false
        features.next returns b thenReturns a thenThrows new NoSuchElementException

        val test = new LazySortedIterator(features, sft, Array(SortBy.NATURAL_ORDER))

        there was no(features).hasNext
        there was no(features).next
        there was no(features).close

        test.next mustEqual a

        there were three(features).hasNext
        there were two(features).next()
        there were one(features).close()
      }
    }

    "be able to sort by id asc" >> {
      val features = CloseableIterator(Iterator(b, c1, d, a, c2))
      val sortBy = Array(SortBy.NATURAL_ORDER)

      test(features, sortBy, Seq(a, b, c1, c2, d), sft)
    }

    "be able to sort by id desc" >> {
      val features = CloseableIterator(Iterator(b, c1, d, a, c2))
      val sortBy =Array(SortBy.REVERSE_ORDER)

      test(features, sortBy, Seq(d, c2, c1, b, a), sft)
    }

    "be able to sort by an attribute asc" >> {
      val features = CloseableIterator(Iterator(b, c2, d, a, c1))
      val sortBy = Array(ff.sort("name", SortOrder.ASCENDING))

      // sort is stable
      test(features, sortBy, Seq(a, b, c2, c1, d), sft)
    }

    "be able to sort by an attribute desc" >> {
      val features = CloseableIterator(Iterator(b, c2, d, a, c1))
      val sortBy = Array(ff.sort("name", SortOrder.DESCENDING))

      // sort is stable
      test(features, sortBy, Seq(d, c2, c1, b, a), sft)
    }

    "be able to sort by an attribute and id" >> {
      val features = CloseableIterator(Iterator(b, c2, d, a, c1))
      val sortBy = Array(ff.sort("name", SortOrder.ASCENDING), SortBy.NATURAL_ORDER)

      test(features, sortBy, Seq(a, b, c1, c2, d), sft)
    }

    "be able to sort by an multiple attributes" >> {
      val features = CloseableIterator(Iterator(a, b, c1, d, c2))
      val sortBy = Array(ff.sort("age", SortOrder.DESCENDING), ff.sort("name", SortOrder.ASCENDING))

      test(features, sortBy, Seq(b, c2, a, c1, d), sft)
    }
  }

  def test(features: CloseableIterator[SimpleFeature],
           sortBy: Array[SortBy],
           expected: Seq[SimpleFeature],
           sft: SimpleFeatureType): MatchResult[Any] = {

    val test = new LazySortedIterator(features, sft, sortBy)

    expected.foreach {f =>
      test.hasNext must beTrue
      test.next mustEqual f
    }
    test.hasNext must beFalse
  }
}