/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

// This is the RasterIndexSchema, for more details see IndexSchema and SchemaHelpers

package org.locationtech.geomesa.raster.index

import org.locationtech.geomesa.accumulo.index.KeyValuePair
import org.locationtech.geomesa.raster.data.Raster

object RasterIndexSchema {

  def encode(raster: Raster, visibility: String = "") = RasterEntryEncoder.encode(raster, visibility)
  def decode(entry: KeyValuePair): Raster = RasterEntryDecoder.decode(entry)

}