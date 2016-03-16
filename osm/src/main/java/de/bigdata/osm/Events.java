package de.bigdata.osm;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Containerklasse fuer die Eventdaten
 * @author Oliver Swoboda
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Events {

	private Bounds bounds;
	// Angabe des Datumsformates fuer die richtige Umwandlung von JSON zu Date
	@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="MM/dd/yyyy")
	private Date dateFrom;
	@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="MM/dd/yyyy")
	private Date dateTo;
	
	private List<Event> events = new ArrayList<Event>();
	private List<String> eventIDs = new ArrayList<String>();
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

	public List<Event> getEvents() {
		return events;
	}
	public void setEvents(List<Event> events) {
		this.events = events;
	}	
	public void addEvent(Event event) {
		events.add(event);
	}	
	
	public List<String> getEventIDs() {
		return eventIDs;
	}
	public void setEventIDs(List<String> eventIDs) {
		this.eventIDs = eventIDs;
	}

	public List<String> getKeywords() {
		return keywords;
	}
	public void setKeywords(List<String> keywords) {
		this.keywords = keywords;
	}
}
