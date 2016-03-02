/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.cassandra.data;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class CassandraDataStoreTestJava {

    @BeforeClass
    public static void init() {
        CassandraDataStoreTest.startServer();
    }

    @Test
    public void testDataAccess() throws IOException {
        Map<String, ?> params = ImmutableMap.of(
                CassandraDataStoreParams.CONTACT_POINT().getName() , CassandraDataStoreTest.CP(),
                CassandraDataStoreParams.KEYSPACE().getName()      , "geomesa_cassandra",
                CassandraDataStoreParams.NAMESPACE().getName()     , "CassandraDataStoreTestJava");
        DataStore ds = DataStoreFinder.getDataStore(params);
        Assert.assertNotNull("DataStore must not be null", ds);
        ds.createSchema(SimpleFeatureTypes.createType("test", "testjavaaccess", "foo:Int,dtg:Date,*geom:Point:srid=4326"));
        Assert.assertTrue("Types should contain testjavaaccess", Collections2.filter(Arrays.asList(ds.getTypeNames()), Predicates.equalTo("testjavaaccess")).size() == 1);
    }
}
