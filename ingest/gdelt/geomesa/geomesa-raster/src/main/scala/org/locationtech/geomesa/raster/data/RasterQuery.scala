/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.raster.data

import org.joda.time.DateTime
import org.locationtech.geomesa.utils.geohash.BoundingBox

/**
 * This class contains parameters needed to create query to
 * retrieve raster chunks from Accumulo table.
 *
 * @param bbox Bounding box defines geometric area of desired raster
 * @param resolution Desired resolution of grid
 * @param startTime Optional earliest ingestion time of rasters
 * @param endTime Optional latest ingestion time of rasters
 */
case class RasterQuery(bbox: BoundingBox,
                       resolution: Double,
                       startTime: Option[DateTime],
                       endTime: Option[DateTime])

// TODO: WCS: include a list of bands as an optional parameter
// ticket is GEOMESA-559
