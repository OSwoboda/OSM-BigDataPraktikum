var rootURL = 'http://localhost:8080/osm/rest/jersey';

var vectors;
var circleLayer = new OpenLayers.Layer.Vector("Circles", {
	styleMap: new OpenLayers.StyleMap({
		'default': OpenLayers.Util.extend({graphicZIndex:'${results}'-'${count}'}, OpenLayers.Feature.Vector.style['default'])
	}),
	rendererOptions: {zIndexing: true}
});
var heatmap = new Heatmap.Layer("Heatmap", {visibility: false});
var box;
var transform;
var map;
var filter = {
		dateFrom: "01/01/2016",
		dateTo: "01/01/2016"
};
var size = 1, maxRadius, minRadius;

function endDrag(bbox) {
	calcMinMaxRadius(bbox);
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
	doUnselect();
	OpenLayers.Util.getElement("results").innerHTML = "Searching...";
	$.ajax({
		method: "POST",
		url: rootURL,
		contentType: "application/json",
		data: JSON.stringify(filter),
		dataType: "json",
		error: function(jqXHR, textStatus, errorThrown) {
			OpenLayers.Util.getElement("results").innerHTML = textStatus;
		},
		success: function(data) {
			OpenLayers.Util.getElement("results").innerHTML = data.events.length > 0 ? data.events.length+" results" : "No results";
			var points = {};
			heatmap.removeAllSources();
			data.events.forEach(function(event) {
				heatmap.addSource(new Heatmap.Source(new OpenLayers.LonLat(event.lon, event.lat).transform(
						new OpenLayers.Projection("EPSG:4326"), // transform from WGS 1984
						new OpenLayers.Projection("EPSG:900913") // to Spherical Mercator
				)));
				var key = event.lat+"&"+event.lon;
				if (points.hasOwnProperty(key)) {
					points[key].count += 1;
					points[key].events.push(event);
				} else {
					points[key] = {count:1, lat:event.lat, lon:event.lon, events:[event]};
				}
			});
			heatmap.redraw();
			circleLayer.removeAllFeatures();
			$.each(points, function(k, v) {				
				var lonlat = new OpenLayers.LonLat(v.lon, v.lat).transform(
						new OpenLayers.Projection("EPSG:4326"), // transform from WGS 1984
						new OpenLayers.Projection("EPSG:900913") // to Spherical Mercator
				);

				var radius = Math.max(minRadius, Math.min(maxRadius, v.count*1000*size));			
				var circle = OpenLayers.Geometry.Polygon.createRegularPolygon
				(
						new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat),
						radius,
						40,
						0
				);

				var featurecircle = new OpenLayers.Feature.Vector(circle, {events:v.events, count:v.count, results:data.events.length});
				circleLayer.addFeatures(featurecircle);
			});
		}
	})
}

function calcMinMaxRadius(geometry) {
	var area = geometry.getArea();
	var radius = Math.sqrt(area/Math.PI);
	maxRadius = radius*size/4;
	minRadius = radius*size/40;
}

function init() {
	map = new OpenLayers.Map("map");
	map.addLayer(new OpenLayers.Layer.OSM());
	map.addControl(new OpenLayers.Control.LayerSwitcher());

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
	map.addLayer(heatmap);

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
		calcMinMaxRadius(event.feature.geometry);
		filter.bounds = new OpenLayers.Bounds(event.feature.geometry.bounds.toArray());
		filter.bounds.transform("EPSG:900913", "EPSG:4326");
		communicate();
	});
	map.addControl(transform);
	var select = new OpenLayers.Control.SelectFeature([vectors, circleLayer], {
		onSelect: function(feature) {
			var events = feature.attributes.events;
			var table = "<table><tr>";
			for (var key in events[0]) {
				table += "<th>"+key+"</th>";
			}
			table += "</tr>";
			$.each(events, function(k, v) {
				table += "<tr>";
				for (var key in v) {
					switch(key) {
					case "sqlDate":
						table += "<td>"+new Date(v[key]).toDateString()+"</td>";
						break;
					case "sourceURL":
						table += "<td><a href="+v[key]+" target='_blank'>Source</a></td>";
						break;
					default:
						table += "<td>"+v[key]+"</td>";
					}					
				}
				table += "</tr>";
			});
			table += "</table>"
			OpenLayers.Util.getElement("eventInfo").innerHTML = table;
			$("#accordion").accordion("option", "active", 0);
		}, 
		onUnselect: doUnselect
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
		circleLayer.redraw();
	});
	$("#dateFrom").val(filter.dateFrom);
	$("#dateFrom").datepicker({
	    defaultDate: filter.dateFrom,
	    maxDate: filter.dateTo,
	    changeMonth: true,
	    changeYear: true,
	    showWeek: true,
		showOtherMonths: true,
	    selectOtherMonths: true,
	    onClose: function(selectedDate) {
	    	$("#dateTo").datepicker("option", "minDate", selectedDate);
	    	filter.dateFrom = selectedDate;
	    }
	});
	$("#dateTo").val(filter.dateTo);
	$("#dateTo").datepicker({
		defaultDate: filter.dateTo,
	    minDate: filter.dateFrom,
		changeMonth: true,
		changeYear: true,
		showWeek: true,
		showOtherMonths: true,
	    selectOtherMonths: true,
		onClose: function(selectedDate) {
			$("#dateFrom").datepicker("option", "maxDate", selectedDate);
			filter.dateTo = selectedDate;
		}
	});
	$("#accordion").accordion({
		collapsible: true,
		active: false,
		heightStyle: "fill"
	});
	
	map.setCenter(lonlat, zoom);
}

function doUnselect() {
	OpenLayers.Util.getElement("eventInfo").innerHTML = "<p>Nothing selected</p>"
	$("#accordion").accordion("option", "active", false);
}

