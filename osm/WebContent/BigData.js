var rootURL = 'http://localhost:8080/osm/rest/jersey';

var vectors;
var circleLayer = new OpenLayers.Layer.Vector("Circles", {
	styleMap: new OpenLayers.StyleMap({
		'default': OpenLayers.Util.extend({graphicZIndex:'${results}'-'${count}'}, OpenLayers.Feature.Vector.style['default'])
	}),
	rendererOptions: {zIndexing: true}
});
var box;
var transform;
var map;
var filter = {
		dateFrom: {month: 02, day: 02, year: 2013},
		dateTo: {month: 02, day: 02, year: 2014},
		hours: {
			from: 0,
			to: 24
		}
};
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
}

function communicate() {
	OpenLayers.Util.getElement("results").innerHTML = "Searching...";
	$.ajax({
		method: "POST",
		url: rootURL,
		contentType: "application/json",
		data: JSON.stringify(filter),
		dataType: "json",
		success: function(data) {
			console.log(data);
			OpenLayers.Util.getElement("results").innerHTML = data.events.length > 0 ? data.events.length+" results" : "No results";
			var points = {};
			data.events.forEach(function(event) {
				var key = event.lat+"&"+event.lon;
				if (points.hasOwnProperty(key)) {
					points[key].count += 1;
					points[key].events.push(event);
				} else {
					points[key] = {count:1, lat:event.lat, lon:event.lon, events:[event]};
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

				var featurecircle = new OpenLayers.Feature.Vector(circle, {events:v.events, count:v.count, results:data.events.length});
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
	box.activate();	

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
	var select = new OpenLayers.Control.SelectFeature([vectors, circleLayer]);
	select.events.register("featurehighlighted", select, function(event) {
		console.log(event);
		console.log(event.feature.attributes);
	});
	map.addControl(select);
	select.activate();
	
	$('#filter').click(function(e) {
		e.preventDefault();
		var eventIDs = $('#events').val().split(/[, ]+/);
		filter.eventIDs = (eventIDs[0] == "" ? [] : eventIDs);
		var keywords = $('#keywords').val().split(/[, ]+/);
		filter.keywords = (keywords[0] == "" ? [] : keywords);
		if (vectors.features.length > 0) {
			communicate();
		}
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
	$("#dateFrom").datepicker({
	    defaultDate: filter.dateFrom.month+"/"+filter.dateFrom.day+"/"+filter.dateFrom.year,
	    maxDate: filter.dateTo.month+"/"+filter.dateTo.day+"/"+filter.dateTo.year,
	    changeMonth: true,
	    changeYear: true,
	    showWeek: true,
		showOtherMonths: true,
	    selectOtherMonths: true,
	    onClose: function(selectedDate) {
	    	$("#dateTo").datepicker("option", "minDate", selectedDate);
	    	var date = selectedDate.split("/");
			date = { month: date[0], day: date[1], year: date[2]};
			filter.dateFrom = date;
			communicate();
	    }
	});
	$("#dateTo").datepicker({
		defaultDate: filter.dateTo.month+"/"+filter.dateTo.day+"/"+filter.dateTo.year,
	    minDate: filter.dateFrom.month+"/"+filter.dateFrom.day+"/"+filter.dateFrom.year,
		changeMonth: true,
		changeYear: true,
		showWeek: true,
		showOtherMonths: true,
	    selectOtherMonths: true,
		onClose: function(selectedDate) {
			$("#dateFrom").datepicker("option", "maxDate", selectedDate);
			var date = selectedDate.split("/");
			date = { month: date[0], day: date[1], year: date[2]};
			filter.dateTo = date;
			communicate();
		}
	});
	$("#slider").slider({
		range: true,
		min: 0,
		max: 24,
		values: [0, 24],
		slide: function(event, ui) {
			$("#hours").val(ui.values[0]+" to "+ui.values[1]);
		},
		stop: function(event, ui) {
			var hours = { from: ui.values[0], to: ui.values[1]};
			filter.hours = hours;
			communicate();
		}
	});
	$("#hours").val($("#slider").slider("values", 0)+" to "+$("#slider").slider("values", 1));
	
	map.setCenter(lonlat, zoom);
}

