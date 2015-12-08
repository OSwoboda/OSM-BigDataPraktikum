var rootURL = 'http://localhost:8080/osm/rest/jersey';

var vectors;
var box;
var transform;

function endDrag(bbox) {
	var bounds = bbox.getBounds();
	var feature = new OpenLayers.Feature.Vector(bounds.toGeometry());
	vectors.addFeatures(feature);
	transform.setFeature(feature);
	box.deactivate();
	communicate(bounds);
}

function communicate(bounds) {
	var json = bounds.transform("EPSG:900913", "EPSG:4326");
	delete json["centerLonLat"];
	$.ajax({
		method: "POST",
		url: rootURL,
		contentType: "application/json",
		data: JSON.stringify(json),
		dataType: "json",
		success: function(data) {
			console.log(data);
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

	box = new OpenLayers.Control.DrawFeature(vectors, OpenLayers.Handler.RegularPolygon, {
		handlerOptions: {
			sides: 4,
			snapAngle: 90,
			irregular: true,
			persist: true
		}
	});
	box.handler.callbacks.done = endDrag;
	map.addControl(box);

	transform = new OpenLayers.Control.TransformFeature(vectors, {
		rotate: false,
		irregular: true
	});
	transform.events.register("transformcomplete", transform, function(event) {
		communicate(new OpenLayers.Bounds(event.feature.geometry.bounds.toArray()));
	});
	map.addControl(transform);

	box.activate();
	
	map.setCenter(lonlat, zoom);
}