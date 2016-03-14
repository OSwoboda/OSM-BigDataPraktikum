package de.bigdata.osm;
import com.google.common.base.Joiner;
import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.locationtech.geomesa.accumulo.index.Constants;
import org.opengis.feature.simple.SimpleFeatureType;

import java.util.ArrayList;
import java.util.List;

/**
 * Copyright 2014 Commonwealth Computer Research, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class GdeltFeature {

    public static enum Attributes {

        GLOBALEVENTID("Integer"),
        SQLDATE("Date"),
        MonthYear("Integer"),
        Year("Integer"),
        FractionDate("Float"),
        Actor1Code("String"),
        Actor1Name("String"),
        Actor1CountryCode("String"),
        Actor1KnownGroupCode("String"),
        Actor1EthnicCode("String"),
        Actor1Religion1Code("String"),
        Actor1Religion2Code("String"),
        Actor1Type1Code("String"),
        Actor1Type2Code("String"),
        Actor1Type3Code("String"),
        Actor2Code("String"),
        Actor2Name("String"),
        Actor2CountryCode("String"),
        Actor2KnownGroupCode("String"),
        Actor2EthnicCode("String"),
        Actor2Religion1Code("String"),
        Actor2Religion2Code("String"),
        Actor2Type1Code("String"),
        Actor2Type2Code("String"),
        Actor2Type3Code("String"),
        IsRootEvent("Integer"),
        EventCode("String"),
        EventBaseCode("String"),
        EventRootCode("String"),
        QuadClass("Integer"),
        GoldsteinScale("Float"),
        NumMentions("Integer"),
        NumSources("Integer"),
        NumArticles("Integer"),
        AvgTone("Float"),
        Actor1Geo_Type("Integer"),
        Actor1Geo_FullName("String"),
        Actor1Geo_CountryCode("String"),
        Actor1Geo_ADM1Code("String"),
        Actor1Geo_Lat("Float"),
        Actor1Geo_Long("Float"),
        Actor1Geo_FeatureID("Integer"),
        Actor2Geo_Type("Integer"),
        Actor2Geo_FullName("String"),
        Actor2Geo_CountryCode("String"),
        Actor2Geo_ADM1Code("String"),
        Actor2Geo_Lat("Float"),
        Actor2Geo_Long("Float"),
        Actor2Geo_FeatureID("Integer"),
        ActionGeo_Type("Integer"),
        ActionGeo_FullName("String"),
        ActionGeo_CountryCode("String"),
        ActionGeo_ADM1Code("String"),
        ActionGeo_Lat("Float"),
        ActionGeo_Long("Float"),
        ActionGeo_FeatureID("Integer"),
        DATEADDED("Integer"),
        geom("Point"),
        SOURCEURL("String");

        private String type;

        private Attributes(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name();
        }
    }

    /**
     * Builds the feature type for the GDELT data set
     *
     * @param featureName
     * @return
     * @throws SchemaException
     */
    public static SimpleFeatureType buildGdeltFeatureType(String featureName) throws SchemaException {

        List<String> attributes = new ArrayList<String>();
        for (Attributes attribute : Attributes.values()) {
            if (attribute == Attributes.geom) {
                // set geom to be the default geometry for geomesa by adding a *
                attributes.add("*geom:Point:srid=4326");
            } else {
                attributes.add(attribute.name() + ":" + attribute.getType());
            }
        }

        String spec = Joiner.on(",").join(attributes);

        SimpleFeatureType featureType = DataUtilities.createType(featureName, spec);
        //This tells GeoMesa to use this Attribute as the Start Time index
        featureType.getUserData().put(Constants.SF_PROPERTY_START_TIME, Attributes.SQLDATE.name());
        return featureType;
    }
}
