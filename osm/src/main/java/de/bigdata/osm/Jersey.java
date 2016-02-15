package de.bigdata.osm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureStore;
import org.geotools.data.Query;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.text.cql2.CQLException;
import org.locationtech.geomesa.accumulo.data.AccumuloFeatureStore;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

@Path("/jersey")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Jersey {
	
	private Events events;
	private FeatureStore<?,?> featureStore;
	private String simpleFeatureTypeName = "gdelt";
	
	public Jersey() throws IOException {
		Map<String, String> dsConf = new HashMap<String, String>();
    	dsConf.put("user", "root");
    	dsConf.put("password", "P@ssw0rd");
    	dsConf.put("instanceId", "bigdata");
    	dsConf.put("zookeepers", "localhost:2181");
    	dsConf.put("tableName", "gdelt_Ukraine");
        dsConf.put("collectStats", "false");
        DataStore dataStore = DataStoreFinder.getDataStore(dsConf);
        assert dataStore != null;

        // get the feature store used to query the GeoMesa data
        featureStore = (AccumuloFeatureStore) dataStore.getFeatureSource(simpleFeatureTypeName);
	}
  
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Events getBounds(Events events) throws IOException, CQLException {
		this.events = events;		
        
		// start with our basic filter to narrow the results
        Filter cqlFilter = createBaseFilter();

        // use the 2-arg constructor for the query - this will not restrict the attributes returned
        Query query = new Query(simpleFeatureTypeName, cqlFilter);

        // execute the query
        FeatureCollection<?,?> results = featureStore.getFeatures(query);

        // loop through all results
        FeatureIterator<?> iterator = results.features();
        try {
            if (iterator.hasNext()) {
                System.out.println("Results:");
            } else {
                System.out.println("No results");
            }
            int n = 0;
            while (iterator.hasNext()) {
                Feature feature = iterator.next();
                StringBuilder result = new StringBuilder();
                result.append(++n);

                for (GdeltFeature.Attributes attribute : GdeltFeature.Attributes.values()) {
                    try {
                        Property property = feature.getProperty(attribute.getName());
                        appendResult(result, property);
                    } catch (Exception e) {
                        // GEOMESA-280 - currently asking for non-existing properties throws an NPE
                    }
                }
                System.out.println(result.toString());

                double lat = Double.valueOf(feature.getProperty(GdeltFeature.Attributes.ActionGeo_Lat.getName()).getValue().toString());
                double lon = Double.valueOf(feature.getProperty(GdeltFeature.Attributes.ActionGeo_Long.getName()).getValue().toString());
                events.addEvent(new Event(lat, lon));
            }
            System.out.println();
        } finally {
            iterator.close();
        }
        
		return events;
	}
	
	private Filter createBaseFilter() throws CQLException, IOException {

        // Get a FilterFactory2 to build up our query
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        List<Filter> filterList = new ArrayList<Filter>();

        // We are going to query for events in Ukraine during the
        // civil unrest.

        // We'll start by looking at a particular day in February of 2014
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, 2014);
        calendar.set(Calendar.MONTH, Calendar.FEBRUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        Date start = calendar.getTime();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        Date end = calendar.getTime();

        Filter timeFilter =
                ff.between(ff.property(GdeltFeature.Attributes.SQLDATE.getName()),
                           ff.literal(start),
                           ff.literal(end));
        filterList.add(timeFilter);
        
        Filter spatialFilter =
                ff.bbox(GdeltFeature.Attributes.geom.getName(),
                        events.getBounds().getLeft(),
                        events.getBounds().getBottom(),
                        events.getBounds().getRight(),
                        events.getBounds().getTop(),
                        "EPSG:4326");
        filterList.add(spatialFilter);

        List<Filter> eventsFilter = new ArrayList<Filter>();
        for (int eventID : events.getEventIDs()) {
        	eventsFilter.add(ff.like(ff.property(GdeltFeature.Attributes.EventCode.getName()), eventID+"%"));
        }
        if (!eventsFilter.isEmpty()) {
        	Filter orEvents = ff.or(eventsFilter);
        	filterList.add(orEvents);
        }
        
        List<Filter> keywordsFilter = new ArrayList<Filter>();
        for (String keyword : events.getKeywords()) {
        	List<Filter> columnFilter = new ArrayList<Filter>();
        	columnFilter.add(ff.like(ff.property(GdeltFeature.Attributes.Actor1Name.getName()), "%"+keyword+"%"));
        	columnFilter.add(ff.like(ff.property(GdeltFeature.Attributes.Actor2Name.getName()), "%"+keyword+"%"));
        	keywordsFilter.add(ff.or(columnFilter));
        }
        if (!keywordsFilter.isEmpty()) {
        	Filter orKeywords = ff.or(keywordsFilter);
        	filterList.add(orKeywords);
        }
        
        // Now we can combine our filters using a boolean AND operator
        Filter conjunction = ff.and(filterList);

        return conjunction;
    }
	
	private void appendResult(StringBuilder string, Property property) {
        if (property != null) {
            string.append("|")
                  .append(property.getName())
                  .append('=')
                  .append(property.getValue());
        }
    }
  
} 