package de.bigdata.osm;

import java.util.ArrayList;
import java.util.List;

public class Events {

	private Bounds bounds;
	
	private List<Event> events = new ArrayList<Event>();
	private List<Integer> eventIDs = new ArrayList<Integer>();

	public Events() {
	}
	
	public Bounds getBounds() {
		return bounds;
	}

	public void setBounds(Bounds bounds) {
		this.bounds = bounds;
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
}
