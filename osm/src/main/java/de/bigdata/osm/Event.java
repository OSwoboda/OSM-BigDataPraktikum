package de.bigdata.osm;

import java.util.Date;

public class Event {
	
	private double lat;
	private double lon;
	private int eventCode;
	
	private Date sqlDate;
	private String actor1Name;
	private String actor2Name;
	private String geoName;
	
	public Event(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}
	
	public double getLat() {
		return lat;
	}
	public void setLat(double lat) {
		this.lat = lat;
	}
	
	public double getLon() {
		return lon;
	}
	public void setLon(double lon) {
		this.lon = lon;
	}

	public int getEventCode() {
		return eventCode;
	}
	public void setEventCode(int eventCode) {
		this.eventCode = eventCode;
	}

	public Date getSqlDate() {
		return sqlDate;
	}
	public void setSqlDate(Date sqlDate) {
		this.sqlDate = sqlDate;
	}

	public String getActor1Name() {
		return actor1Name;
	}
	public void setActor1Name(String actor1Name) {
		this.actor1Name = actor1Name;
	}

	public String getActor2Name() {
		return actor2Name;
	}
	public void setActor2Name(String actor2Name) {
		this.actor2Name = actor2Name;
	}

	public String getGeoName() {
		return geoName;
	}
	public void setGeoName(String geoName) {
		this.geoName = geoName;
	}
}
