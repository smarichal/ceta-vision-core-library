package edu.ceta.vision.core.utils;

import java.util.ArrayList;
import java.util.Iterator;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import edu.ceta.vision.core.topcode.TopCode;

public class SpotsCache {

	int size, position;
	ArrayList<TopCode>[] array;
	
	public SpotsCache(int size){
		this.size = size;
		this.array = new ArrayList[size];
		this.position = 0;
		for(int i=0;i<size;i++){
			this.array[i] = null;
		}
	}
	
	public void insert(ArrayList<TopCode> spotList){
		this.array[position]=spotList;
		this.position = (this.position+1)%this.size;
		
	}
	
	public TopCode isInCache(int spotCode, Rect area){
		int j = 0;
		for(int i=this.position-1;j<size;j++){
			if(i<0){
				i=size-1;
			}
			if(array[i]!=null){
				for(Iterator<TopCode> iter = array[i].iterator();iter.hasNext();){
					TopCode cachedSpot = iter.next();
					if(cachedSpot.getCode()==spotCode){ //we found the spot, let's check if it is inside the area
						if(area.contains(new Point(cachedSpot.getCenterX(),cachedSpot.getCenterY()))){
							return cachedSpot;
						}
					}
				}
			}
			i--;
			
		}
		return null;
	}
	
}
