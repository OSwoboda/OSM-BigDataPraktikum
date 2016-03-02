/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.utils.geohash

import org.junit.{Assert, Test}

class BoundingBoxGeoHashIteratorTest {
  @Test
  def testBoundingBoxGeoHashIteratorTest() {
    // Great pictures at http://www.bigdatamodeling.org/2013/01/intuitive-geohash.html
    val latRow = new BoundingBoxGeoHashIterator(new TwoGeoHashBoundingBox(GeoHash("d0"),
                                                                          GeoHash("d8")))

    val lonRow = new BoundingBoxGeoHashIterator(new TwoGeoHashBoundingBox(GeoHash("d8"),
                                                                          GeoHash("dw")))

    val box16 = new BoundingBoxGeoHashIterator(new TwoGeoHashBoundingBox(GeoHash("dh"),
                                                                         GeoHash("dz")))

    val cross = new BoundingBoxGeoHashIterator(new TwoGeoHashBoundingBox(GeoHash("d0"),
                                                                         GeoHash("g8")))

    def testAndCheck(bbghi: BoundingBoxGeoHashIterator, list: List[String]) {
      val answer = for(i <- 1 to bbghi.ns) yield bbghi.next.hash
      Assert.assertEquals("The list returned was wrong", answer.toList, list)
      try {
        bbghi.next
        Assert.fail("should have thrown NoSuchElementException")
      } catch {
        case nsee: NoSuchElementException =>
      }
    }

    testAndCheck(cross,
      List("d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "db", "dc", "dd", "de",
        "df", "dg", "dh", "dj", "dk", "dm", "dn", "dp", "dq", "dr", "ds", "dt", "du", "dv", "dw",
        "dx", "dy", "dz", "e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ed", "ee",
        "eh", "ej", "ek", "em", "en", "ep", "eq", "er", "es", "et", "ew", "ex", "f0", "f2", "f8",
        "fb", "g0", "g2", "g8"))

    testAndCheck(box16,
      List("dh", "dj", "dk", "dm", "dn", "dp", "dq", "dr",
        "ds", "dt", "du", "dv", "dw", "dx", "dy", "dz"))

    testAndCheck(latRow, List("d0", "d2", "d8"))

    testAndCheck(lonRow, List("d8", "d9", "dd", "de", "ds", "dt", "dw"))

  }
}
