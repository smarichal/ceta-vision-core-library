package edu.ceta.vision.core.topcode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class Matrix {

	private int max_markers;
	private HashMap<TopCode,Double> data;
	
	public Matrix(int max_markers){
		this.max_markers = max_markers;
		this.data = new HashMap<TopCode, Double>(max_markers);		
		this.clear();
	}
	
	public void updateProbability(TopCode marker, double P){
		this.data.put(marker, Double.valueOf(P));
	}
	
	public void remove(TopCode marker){
		this.data.remove(marker);
	}
	
	public void clear(){
		this.data.clear();
	}
	
	public void insert(TopCode marker, double P){
		this.data.put(marker, Double.valueOf(P));
//		boolean inserted = false;
//		for(int i=0;(i<max_markers)&&(!inserted);i++){
//			if(this.data[i][1]==-1){
//				this.data[i][0] = marker;
//				this.data[i][1] = P;
//				inserted=true;
//			}
//		}
//		if(!inserted){ //matrix is full, override first row. This should not happen!
//			this.data[0][0] = marker;
//			this.data[0][1] = P;
//		}
	}

	public void addAll(List<TopCode> markers){
		for(Iterator<TopCode> iter = markers.iterator();iter.hasNext();){
			TopCode marker = iter.next();
			this.insert(marker, 1);
		}
	}

	public void updateAllProbs(List<TopCode> markers, double step) {
		TopCode auxMarker, detectedMarker;
		Double auxProb;
		for(Iterator<Entry<TopCode,Double>> iter = this.data.entrySet().iterator();iter.hasNext();){
			Entry<TopCode,Double> entry = iter.next();
			auxMarker = entry.getKey();
			auxProb = entry.getValue();
			
			int index = markers.indexOf(auxMarker);
			if(index!=-1){ //marker was detected
				entry.setValue(Double.valueOf(1));
				detectedMarker = markers.get(index);
				auxMarker.setDiameter(detectedMarker.getDiameter());
				auxMarker.setOrientation(detectedMarker.getOrientation());
				auxMarker.setLocation(detectedMarker.getCenterX(), detectedMarker.getCenterY());

				markers.remove(auxMarker);	//remove it in order to not process it again in the bellow in the iteration through the detected markers list
			}else{							//marker was not detected
				if(auxProb - step <= 0){ 
					iter.remove();
				}else{
					entry.setValue(auxProb - step);
				}
			}
		}
		//Add all the remaining detected markers
		for(Iterator<TopCode> iter = markers.iterator();iter.hasNext();){
			this.data.put(iter.next(),Double.valueOf(1));
		}
	}
	
	public Set<TopCode> getStateMarkers(){
		return this.data.keySet();
	}
}
