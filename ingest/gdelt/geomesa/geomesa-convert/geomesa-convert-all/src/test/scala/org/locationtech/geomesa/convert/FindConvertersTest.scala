/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.convert

import org.junit.runner.RunWith
import org.locationtech.geomesa.convert.avro.{AvroSimpleFeatureConverterFactory, AvroSimpleFeatureConverter}
import org.locationtech.geomesa.convert.fixedwidth.{FixedWidthConverterFactory, FixedWidthConverter}
import org.locationtech.geomesa.convert.json.{JsonSimpleFeatureConverterFactory, JsonSimpleFeatureConverter}
import org.locationtech.geomesa.convert.text.{DelimitedTextConverterFactory, DelimitedTextConverter}
import org.locationtech.geomesa.convert.xml.{XMLConverterFactory, XMLConverter}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FindConvertersTest extends Specification {

  "geomesa convert all" should {
    "find all classes for converters" >> {
      classOf[AvroSimpleFeatureConverter] must not(throwAn[ClassNotFoundException])
      classOf[AvroSimpleFeatureConverterFactory] must not(throwAn[ClassNotFoundException])

      classOf[FixedWidthConverter] must not(throwAn[ClassNotFoundException])
      classOf[FixedWidthConverterFactory] must not(throwAn[ClassNotFoundException])

      classOf[JsonSimpleFeatureConverter] must not(throwAn[ClassNotFoundException])
      classOf[JsonSimpleFeatureConverterFactory] must not(throwAn[ClassNotFoundException])

      classOf[DelimitedTextConverter] must not(throwAn[ClassNotFoundException])
      classOf[DelimitedTextConverterFactory] must not(throwAn[ClassNotFoundException])

      classOf[XMLConverter] must not(throwAn[ClassNotFoundException])
      classOf[XMLConverterFactory] must not(throwAn[ClassNotFoundException])

      classOf[CompositeConverter[_]] must not(throwAn[ClassNotFoundException])
      classOf[CompositeConverterFactory[_]] must not(throwAn[ClassNotFoundException])
      classOf[SimpleFeatureConverterFactory[_]] must not(throwAn[ClassNotFoundException])
    }

    "register all the converters" >> {
      SimpleFeatureConverters.providers.map(_.getClass) must containTheSameElementsAs(
        Seq(
          classOf[AvroSimpleFeatureConverterFactory],
          classOf[FixedWidthConverterFactory],
          classOf[DelimitedTextConverterFactory],
          classOf[XMLConverterFactory],
          classOf[JsonSimpleFeatureConverterFactory],
          classOf[CompositeConverterFactory[_]]
        )
      )
    }
  }

}
