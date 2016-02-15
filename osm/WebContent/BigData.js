var rootURL = 'http://localhost:8080/osm/rest/jersey';

var vectors;
var circleLayer = new OpenLayers.Layer.Vector("Circles");
var box;
var transform;
var map;
var filter = {};
var size = 1;

function endDrag(bbox) {	
	vectors.removeAllFeatures();
	transform.unsetFeature();
	filter.bounds = bbox.getBounds();
	var feature = new OpenLayers.Feature.Vector(filter.bounds.toGeometry());
	filter.bounds.transform("EPSG:900913", "EPSG:4326");
	vectors.addFeatures(feature);
	transform.setFeature(feature);
	communicate();
	
	var legend = OpenLayers.Util.getElement("legend");
	if (legend.innerHTML.indexOf("Ctrl+LeftClick") > -1) {
		legend.innerHTML =
			"<p class='center' id='results'>Searching...</p>" +
			"<form>" +
				"<p><label class='field' for='events'>Events:</label> <input id='events' name='events'></p>" +
				"<p><label class='field' for='keywords'>Keywords:</label> <input id='keywords' name='keywords'> <input id='filter' type='submit' value='Filter'></p>" +
			"</form>" +
			"<form>" +
				"<p><label class='field' for='circleSize'>circleSize:</label> <input class='circleSize' name='circleSize' type='submit' value='-'> <input class='circleSize' name='circleSize' type='submit' value='+'></p>" +
			"</form>";
		$('#filter').click(function(e) {
			e.preventDefault();
			var eventIDs = $('#events').val().split(/[, ]+/);
			filter.eventIDs = (eventIDs[0] == "" ? [] : eventIDs);
			var keywords = $('#keywords').val().split(/[, ]+/);
			filter.keywords = (keywords[0] == "" ? [] : keywords);
			communicate();
		});
		$('input.circleSize').click(function(e) {
			e.preventDefault();
			var scale = e.toElement.value == "+" ? 2 : 0.5;
			size *= scale;
			circleLayer.features.forEach(function(feature) {
				feature.geometry.resize(scale, feature.geometry.getCentroid());
			});
			circleLayer.redraw()
		});
	}
}

function communicate() {
	$.ajax({
		method: "POST",
		url: rootURL,
		contentType: "application/json",
		data: JSON.stringify(filter),
		dataType: "json",
		success: function(data) {
			OpenLayers.Util.getElement("results").innerHTML = data.events.length > 0 ? data.events.length+" results" : "No results";
			var points = {};
			data.events.forEach(function(event) {
				var key = event.lat+"&"+event.lon;
				if (points.hasOwnProperty(key)) {
					points[key].count += 1;
				} else {
					points[key] = {count:1, lat:event.lat, lon:event.lon};
				}
			});
			circleLayer.removeAllFeatures();
			$.each(points, function(k, v) {				
				var lonlat = new OpenLayers.LonLat(v.lon, v.lat).transform(
						new OpenLayers.Projection("EPSG:4326"), // transform from WGS 1984
						new OpenLayers.Projection("EPSG:900913") // to Spherical Mercator
				);
				var circle = OpenLayers.Geometry.Polygon.createRegularPolygon
				(
						new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat),
						v.count*1000*size,
						40,
						0
				);

				var featurecircle = new OpenLayers.Feature.Vector(circle);
				circleLayer.addFeatures(featurecircle);
			});
			circleLayer.redraw();
		}
	})
}

function init() {
	map = new OpenLayers.Map("map");
	map.addLayer(new OpenLayers.Layer.OSM());

	var lonlat = new OpenLayers.LonLat(10.45415, 51.164181).transform(
			new OpenLayers.Projection("EPSG:4326"), // transform from WGS 1984
			new OpenLayers.Projection("EPSG:900913") // to Spherical Mercator
	);

	var zoom = 5;

	vectors = new OpenLayers.Layer.Vector("Vector Layer", {
		displayInLayerSwitcher: false
	});
	map.addLayer(vectors);
	map.addLayer(circleLayer);	

	box = new OpenLayers.Control.DrawFeature(vectors, OpenLayers.Handler.RegularPolygon, {
		handlerOptions: {
			snapAngle: 90,
			irregular: true,
			persist: false,
			keyMask: OpenLayers.Handler.MOD_CTRL
		}
	});
	box.handler.callbacks.done = endDrag;
	map.addControl(box);

	transform = new OpenLayers.Control.TransformFeature(vectors, {
		rotate: false,
		irregular: true
	});
	transform.events.register("transformcomplete", transform, function(event) {
		filter.bounds = new OpenLayers.Bounds(event.feature.geometry.bounds.toArray());
		filter.bounds.transform("EPSG:900913", "EPSG:4326");
		communicate();
	});
	map.addControl(transform);
	box.activate();	
	
	map.setCenter(lonlat, zoom);
}