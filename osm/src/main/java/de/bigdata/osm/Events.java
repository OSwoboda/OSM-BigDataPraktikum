package de.bigdata.osm;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Events {

	private Bounds bounds;
	private Date dateFrom;
	private Date dateTo;
	private Hours hours;
	
	private List<Event> events = new ArrayList<Event>();
	private List<Integer> eventIDs = new ArrayList<Integer>();
	private List<String> keywords = new ArrayList<String>();

	public Events() {
	}
	
	public Bounds getBounds() {
		return bounds;
	}
	public void setBounds(Bounds bounds) {
		this.bounds = bounds;
	}

	public Date getDateFrom() {
		return dateFrom;
	}
	public void setDateFrom(Date dateFrom) {
		this.dateFrom = dateFrom;
	}

	public Date getDateTo() {
		return dateTo;
	}
	public void setDateTo(Date dateTo) {
		this.dateTo = dateTo;
	}

	public Hours getHours() {
		return hours;
	}
	public void setHours(Hours hours) {
		this.hours = hours;
	}

	public List<Event> getEvents() {
		return events;
	}
	public void setEvents(List<Event> events) {
		this.events = events;
	}	
	public void addEvent(Event event) {
		events.add(event);
	}	
	
	public List<Integer> getEventIDs() {
		return eventIDs;
	}
	public void setEventIDs(List<Integer> eventIDs) {
		this.eventIDs = eventIDs;
	}

	public List<String> getKeywords() {
		return keywords;
	}
	public void setKeywords(List<String> keywords) {
		this.keywords = keywords;
	}
}
