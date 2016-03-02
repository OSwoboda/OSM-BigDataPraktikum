/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa

import java.math.{MathContext, RoundingMode}

import org.calrissian.mango.types.LexiTypeEncoders
import org.geotools.data.DataAccessFactory.Param
import org.locationtech.geomesa.accumulo.data._
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes
import org.locationtech.geomesa.utils.geotools.RichSimpleFeatureType.RichSimpleFeatureType

/**
 * In these lexiEncode and -Decode functions, a double is encoded or decoded into a lexical
 * representation to be used in the rowID in the Accumulo key.
 *
 * In the lexiEncodeDoubleToString function, the double is truncated via floor() to four significant digits,
 * giving a scientific notation of #.###E0. This is to ensure that there is flexibility in the
 * precision of the bounding box given when querying for chunks. Prior to rounding the resolution,
 * it was found that even that slightest change in bounding box caused the resolution to be calculated
 * differently many digits after the decimal point, leading to a different lexical representation of the
 * resolution. This meant that no chunks would be returned out of Accumulo, since they did not match
 * the resolution calculated upon ingest.
 *
 * Now, there is greater flexibility in specifying a bounding box and calculating a resolution because
 * we save only four digits after the decimal point.
 */
package object raster {

  object RasterParams {
    val writeMemoryParam = new Param("writeMemory", classOf[Integer], "The memory allocation to use for writing records", false)
  }

  val rasterSftName: String = ""

  // Raster CQ MetaData SFT
  val rasterSft = {
    val sft = SimpleFeatureTypes.createType("RasterIndexEntry", "*geom:Geometry:srid=4326,dtg:Date")
    sft.setSchemaVersion(CURRENT_SCHEMA_VERSION)
    sft.setDtgField("dtg")
    sft
  }

  // geom field name
  val rasterSftGeomName = "geom"

  // dtg field name
  val rasterSftDtgName = "dtg"

  // defaults
  val defaultResolution = 1.0
  val rasterMajcMaxVers = "1"
  val rasterMincMaxVers = "1"
  val rasterScanMaxVers = "1"
  val rasterSplitThresh = "512M"

  // Sets the rounding mode to use floor() in order to minimize effects from round-off at higher precisions
  val roundingMode =  RoundingMode.FLOOR

  // Sets the scale for the floor() function and thus determines where the truncation occurs
  val significantDigits = 4

  // Defines the rules for rounding using the above
  val mc = new MathContext(significantDigits, roundingMode)

  /**
   * The double, number, is truncated to a certain number of significant digits and then lexiEncoded into
   * a string representations.
   * @param number, the Double to be lexiEncoded
   */
  def lexiEncodeDoubleToString(number: Double): String = {
    val truncatedRes = BigDecimal(number).round(mc).toDouble
    LexiTypeEncoders.LEXI_TYPES.encode(truncatedRes)
  }

  /**
   * The string representation of a double, str, is decoded to its original Double representation
   * and then truncated to a certain number of significant digits to remain consistent with the lexiEncode function.
   * @param str, the String representation of the Double
   */
  def lexiDecodeStringToDouble(str: String): Double = {
    val number = LexiTypeEncoders.LEXI_TYPES.decode("double", str).asInstanceOf[Double]
    BigDecimal(number).round(mc).toDouble
  }

  def lexiEncodeIntToString(number: Int): String = LexiTypeEncoders.LEXI_TYPES.encode(number)

  def lexiDecodeStringToInt(str: String): Int = LexiTypeEncoders.LEXI_TYPES.decode("integer", str).asInstanceOf[Int]

}
