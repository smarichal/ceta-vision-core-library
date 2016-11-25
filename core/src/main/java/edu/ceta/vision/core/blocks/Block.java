package edu.ceta.vision.core.blocks;

import java.util.List;


import org.opencv.core.Point;
import org.opencv.core.Size;

import edu.ceta.vision.core.topcode.TopCode;

public class Block {

	private Point center;
	private double area;
	private double height, width;
	
	/**
	 * rotation in radians
	 */
	private float orientation;
	private Point[] vertices;
	private List<TopCode> spots;
	

	private int type;
	private int value;
	private int spotsId;
	
	public Block(int value) {
		super();
		this.value=value;
	}
	
	public Block(int value, Point center, Size size, Point[] vertices) {
		super();
		this.value = value;
		this.center = center;
		this.area = size.area();
		this.height = size.height;
		this.width = size.width;
		this.vertices = vertices;
		this.spotsId = -1;
	}

	public Point getCenter() {
		return center;
	}

	public void setCenter(Point center) {
		this.center = center;
	}

	public double getArea() {
		return area;
	}

	public void setArea(double area) {
		this.area = area;
	}

	public Point[] getVertices() {
		return vertices;
	}

	public void setVertices(Point[] vertices) {
		this.vertices = vertices;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public List<TopCode> getSpots() {
		return spots;
	}

	public void setSpots(List<TopCode> spots) {
		this.spots = spots;
		if(spots!=null && spots.size()>0){
			this.spotsId=spots.get(0).getCode();
		}
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public float getOrientation() {
		return orientation;
	}

	public void setOrientation(float orientation) {
		this.orientation = orientation;
	}
	
	public int getId(){
		return this.spotsId;
	}
	
	
	
}
