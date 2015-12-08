package de.bigdata.osm;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Events {

	private double top;
	private double right;
	private double bottom;
	private double left;
	
	public Events() {
	}
	
	public Events(double top, double right, double bottom, double left) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
	}
	
	public double getTop() {
		return top;
	}
	public void setTop(double top) {
		this.top = top;
	}
	
	public double getRight() {
		return right;
	}
	public void setRight(double right) {
		this.right = right;
	}
	
	public double getBottom() {
		return bottom;
	}
	public void setBottom(double bottom) {
		this.bottom = bottom;
	}
	
	public double getLeft() {
		return left;
	}
	public void setLeft(double left) {
		this.left = left;
	}
	
}
