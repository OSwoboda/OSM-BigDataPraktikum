/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/


package org.locationtech.geomesa.utils.geotools

import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.geom._
import org.junit.runner.RunWith
import org.locationtech.geomesa.utils.geotools.Conversions.toRichSimpleFeatureIterator
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GridSnapTest extends Specification with LazyLogging {

  "GridSnap" should {
    "create a gridsnap around a given bbox" in {
      val bbox = new Envelope(0.0, 10.0, 0.0, 10.0)
      val gridSnap = new GridSnap(bbox, 100, 100)

      gridSnap must not beNull
    }

    "compute a SimpleFeatureSource Grid over the bbox" in {
      val bbox = new Envelope(0.0, 10.0, 0.0, 10.0)
      val gridSnap = new GridSnap(bbox, 10, 10)

      val grid = gridSnap.generateCoverageGrid

      grid must not beNull

      val featureIterator = grid.getFeatures.features

      val gridLength = featureIterator.length

      gridLength should be equalTo 100

    }

    "compute a sequence of points between various sets of coordinates" in {
      val bbox = new Envelope(0.0, 10.0, 0.0, 10.0)
      val gridSnap = new GridSnap(bbox, 10, 10)

      val resultDiagonal = gridSnap.genBresenhamCoordSet(0, 0, 9, 9).toList
      resultDiagonal must not beNull
      val diagonalLength = resultDiagonal.length
      diagonalLength should be equalTo 9

      val resultVeritcal = gridSnap.genBresenhamCoordSet(0, 0, 0, 9).toList
      resultVeritcal must not beNull
      val verticalLength = resultVeritcal.length
      verticalLength should be equalTo 9

      val resultHorizontal = gridSnap.genBresenhamCoordSet(0, 0, 9, 0).toList
      resultHorizontal must not beNull
      val horizontalLength = resultHorizontal.length
      horizontalLength should be equalTo 9

      val resultSamePoint = gridSnap.genBresenhamCoordSet(0, 0, 0, 0).toList
      resultSamePoint must not beNull
      val samePointLength = resultSamePoint.length
      samePointLength should be equalTo 1
    }

    "check corner cases where GridSnap is given below minimum coordinates of the grid" in {
      val bbox = new Envelope(0.0, 10.0, 0.0, 10.0)
      val gridSnap = new GridSnap(bbox, 100, 100)

      val iReturn = gridSnap.i(bbox.getMinX - 1)
      val jReturn = gridSnap.j(bbox.getMinY - 1)
      iReturn should be equalTo 0
      jReturn should be equalTo 0
      gridSnap.x(iReturn) should be equalTo bbox.getMinX
      gridSnap.y(jReturn) should be equalTo bbox.getMinY
    }
  }

}
