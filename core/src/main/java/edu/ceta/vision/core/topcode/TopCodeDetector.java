package edu.ceta.vision.core.topcode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import edu.ceta.vision.core.blocks.Block;
import edu.ceta.vision.core.utils.BlocksMarkersMap;
import edu.ceta.vision.core.utils.Logger;
import edu.ceta.vision.core.utils.SpotsCache;
import edu.ceta.vision.core.utils.TopCodeSorter;
import edu.ceta.vision.core.utils.TopCodeXComparator;



public abstract class TopCodeDetector {
	protected Scanner scanner;
	protected List<TopCode> markers;
	protected HashMap<Integer, List<TopCode>> groupedMarkers;
	protected Set<Block> blocks;
	protected Matrix stateMatrix;
	protected double step;
	protected double minLength;
	protected boolean probMode;
	
	private TopCodeXComparator topCodeComparator = new TopCodeXComparator();
	private SpotsCache cache;
	private boolean cacheEnabled;
	protected Rect detectionZone = null;
	
	
	protected double DEFAULT_INTERSPOT_DISTANCE = 11;
	
	//This value is computed as the average interspot distance of the markers in spot.units
	protected double interspot_distance ; //In spot.units
	
	private boolean allow_different_spot_distance;
	protected boolean multiple_markers_per_block = true;
	private boolean interspot_distance_computed;
	private int adjust_interspot_distance_count; 
	private static int PROBABLE_AREA_SIDE = 30;
	
	private static int FRAMES_ADJUTS_INTERSPOT_DISTANCE = 60; //after 60 frames we compute the interspot distance again
	
	/*When the topcodes are in horizontal position the orientation of the spots is -4 degrees or -0.07249829 radians*/
	private static int HORIZONTAL_INITIAL_ROTATION_DEGREES = -4;
	private static float HORIZONTAL_INITIAL_ROTATION_RADIANS = -0.07249829f;

	/**
	 * 
	 * @param max_markers Max number of markers allowed
	 * @param probMod: deprecated
	 * @param size_cache: number of frames used in cache
	 * @param cacheEnabled : enabling cache allows the detector to infer the position of the non visible markers and 
	 * 						therefore compute more accuratelly the position and orientation of a block that is partially occluded
	 * @param allow_different_spot_distance: Indicates if it is possible to have markers of different sizes in different blocks. 
	 * 										 The default and recommended value is false.
	 * @param multiple_markers_per_block: Indicates if there are multiple markers within a block or just one marker per block. 
	 * 									 Using multiple markers is computationally more expensive but leads to better results of the 
	 * 									detection algorithm. 
	 */
	public TopCodeDetector(int max_markers, boolean probMode, int size_cache, boolean cacheEnabled,
			boolean allow_different_spot_distance,boolean multiple_markers_per_block, Rect detectionZone){
		this.stateMatrix = new Matrix(max_markers);
		this.step = 0.5;
		this.probMode = probMode;
		this.minLength = 20; //FIXME check this value with real tests
		this.groupedMarkers = new HashMap<Integer, List<TopCode>>(max_markers);
		this.blocks = new HashSet<Block>();
		this.cacheEnabled = cacheEnabled;
		this.cache = new SpotsCache(size_cache);
		this.allow_different_spot_distance=allow_different_spot_distance;
		this.interspot_distance = DEFAULT_INTERSPOT_DISTANCE;
		this.interspot_distance_computed=false;
		this.adjust_interspot_distance_count = 0;
		this.multiple_markers_per_block = multiple_markers_per_block;
		this.detectionZone = detectionZone;
	}
	
	
	
	
	protected void groupMarkers(){
		this.groupedMarkers.clear();
		for(Iterator<TopCode> iter = this.markers.iterator();iter.hasNext();){
			TopCode marker = iter.next();
			List<TopCode> list = new ArrayList<TopCode>();
			list.add(marker);
			iter.remove();
			for(Iterator<TopCode> iter2 = this.markers.iterator();iter2.hasNext();){
				TopCode marker2 = iter2.next();
				if(marker2.getCode() == marker.getCode()){
					list.add(marker2);
					iter2.remove();
				}
			}
			iter=this.markers.iterator();
			this.groupedMarkers.put(Integer.valueOf(marker.getCode()), list);
		}
		if(!interspot_distance_computed){
			this.interspot_distance = computeInterspotDistance(this.groupedMarkers);
		}
	}
	
	private double computeInterspotDistance(HashMap<Integer, List<TopCode>> grouped_markers) {
		List<Double> distances = new ArrayList<Double>();
		Set<Entry<Integer,List<TopCode>>> entrySet = grouped_markers.entrySet();
		for(Iterator<Entry<Integer,List<TopCode>>> iter = entrySet.iterator();iter.hasNext();){
			Entry<Integer,List<TopCode>> entry = iter.next();
			List<TopCode> spotList = entry.getValue();
			int size = spotList.size();
			//we need at least 2 spots to compute their distance and the block has to be complete, i.e all the spots visibles
			if(spotList!=null && size>1 && BlocksMarkersMap.belongsToBlockClass(size, entry.getKey())){	
				double d = 0;
				TopCode spot1=null,spot2=null;
				float unit = 0;
				for(int i=0;i<spotList.size()-1;i++){
					spot1 = spotList.get(i);
					spot2 = spotList.get(i+1);
					unit+= spot1.unit;
					d+=getDistance(spot1, spot2);
				}
				unit+=spot2.unit;
				unit/=spotList.size();
				if(d>0){
					d/= (spotList.size()-1);
					d/=unit;
					distances.add(d);
				}
			}
		}
		double ret=0;
		if(distances.size()>0){
			interspot_distance_computed=true;
			for(Iterator<Double> iter=distances.iterator();iter.hasNext();){
				ret+=iter.next();
			}
			ret/=distances.size();
		}else{
			ret= this.interspot_distance; //keep the actual value. At the beginning this value is DEFAULT_INTERSPOT_DISTANCE
		}
		return ret;
	}

	
	protected void computeSingleMarkersBlocks(){
		this.blocks.clear();
		ArrayList<TopCode> accumulatedSpots = new ArrayList<TopCode>();
		for(Iterator<TopCode> iter = this.markers.iterator();iter.hasNext();){
			TopCode spot = iter.next();
			Block block = computeSingleMarkerBlock(spot);
			if(block!=null){
				this.blocks.add(block);
				accumulatedSpots.addAll(block.getSpots());
			}else{
				Logger.warning("Unrecognized block! marker value= " + spot.getCode());
			}
		}
	}
	
	private Block computeSingleMarkerBlock(TopCode spot) {
		Block block = null;
		int markerCode = spot.getCode();
		int type = -1;
		if(BlocksMarkersMap.belongsToBlockClass(1, markerCode)){
			block = new Block(1);
			type = 1;
		}else if(BlocksMarkersMap.belongsToBlockClass(2, markerCode)){
			block = new Block(2);
			type = 2;
		}else if(BlocksMarkersMap.belongsToBlockClass(3, markerCode)){
			block = new Block(3);
			type = 3;
		}else if(BlocksMarkersMap.belongsToBlockClass(4, markerCode)){
			block = new Block(4);
			type = 4;
		}else if(BlocksMarkersMap.belongsToBlockClass(5, markerCode)){
			block = new Block(5);
			type = 5;
		}
		if(block!=null){
			block.setType(type);
			block.setSpots(markers);
			block.setOrientation(computeBlockOrientation(block));
			Point widthAndHeight = computeWidthAndHeightSinglemarkerBlock(block);
			block.setArea(widthAndHeight.x*widthAndHeight.y);
			block.setWidth(widthAndHeight.x);
			block.setHeight(widthAndHeight.y);
			block.setCenter(computeCenterSingleMarkerBlock(spot, block.getType()));
		}
		return block;
	}

	private Point computeCenterSingleMarkerBlock(TopCode spot, int type) {
		if(type==1){
			return new Point(spot.x,spot.y);
		}else{
			Point deltas = getDeltas(spot, type-1);
			TopCode rightExtremePoint = getRightProjectedSpot(spot, deltas.x,deltas.y);
			List<TopCode> list = new ArrayList<TopCode>();
			list.add(spot);
			list.add(rightExtremePoint);
			return getMiddlePoint(list);
		}
	}

	protected void computeMultiMarkersBlocks(){
		this.blocks.clear();
		ArrayList<TopCode> accumulatedSpots = new ArrayList<TopCode>();
		Set<Entry<Integer, List<TopCode>>> map=  this.groupedMarkers.entrySet();
		for(Iterator<Entry<Integer,List<TopCode>>> iter = map.iterator();iter.hasNext();){
			Entry<Integer, List<TopCode>> entry = iter.next();
			Block block = computeBlock(entry.getKey().intValue(),entry.getValue());
			if(block!=null){
				this.blocks.add(block);
				accumulatedSpots.addAll(block.getSpots());
			}else{
				Logger.warning("Unrecognized block! marker value= " + entry.getKey());
			}
		}
		if(this.cacheEnabled){			
			this.cache.insert(accumulatedSpots); //save all the spots in cache
		}
		this.adjust_interspot_distance_count++;
		if(this.adjust_interspot_distance_count==FRAMES_ADJUTS_INTERSPOT_DISTANCE){
			this.interspot_distance_computed=false;
			this.adjust_interspot_distance_count = 0;
		}
	}

	
	protected Block computeBlock(int markerCode, List<TopCode> markers) {
		Block block = null;
		int type = -1;
		if(BlocksMarkersMap.belongsToBlockClass(1, markerCode)){
			block = processBlockClass1(markers);
			type = 1;
		}else if(BlocksMarkersMap.belongsToBlockClass(2, markerCode)){
			block = processBlockClass2(markers, block);
			type = 2;
		}else if(BlocksMarkersMap.belongsToBlockClass(3, markerCode)){
			block = processBlockClass3(markers, block);
			type = 3;
		}else if(BlocksMarkersMap.belongsToBlockClass(4, markerCode)){
			block = processBlockClass4(markers, block);
			type = 4;
		}else if(BlocksMarkersMap.belongsToBlockClass(5, markerCode)){
			block = processBlockClass5(markers, block);
			type = 5;
		}
		if(block!=null){
			block.setType(type);
			block.setSpots(markers);
			block.setOrientation(computeBlockOrientation(block));
			Point widthAndHeight = computeWidthAndHeightMultimarkerBlock(block);
			block.setArea(widthAndHeight.x*widthAndHeight.y);
			block.setWidth(widthAndHeight.x);
			block.setHeight(widthAndHeight.y);
		}
		return block;
	}

	protected Block processBlockClass5(List<TopCode> markers, Block block) {
		if(markers.size()==5){
			Point center = getMiddlePoint(markers);
			block = new Block(5);
			block.setCenter(center);
		}else{
			List<TopCode> missingSpots = getMissingSpotInBlock5(markers);
			markers.addAll(missingSpots);
			block = new Block(5);
			block.setCenter(getMiddlePoint(markers));
		}
		return block;
	}
	
	protected Block processBlockClass4(List<TopCode> markers, Block block) {
		if(markers.size()==4){
			Point center = getMiddlePoint(markers);
			block = new Block(4);
			block.setCenter(center);
		}else{
			List<TopCode> missingSpots = getMissingSpotInBlock4(markers); 
			markers.addAll(missingSpots);
			block = new Block(4);
			block.setCenter(getMiddlePoint(markers));		
		} 
		return block;
	}

	protected Block processBlockClass3(List<TopCode> markers, Block block) {
		if(markers.size()==3){
			TopCode middleMarker = getMarkerInTheMiddle(markers);
			block = new Block(3);
			block.setCenter(new Point(middleMarker.getCenterX(),middleMarker.getCenterY()));
		}else if(markers.size()==2){
			TopCode spot1 = markers.get(0);
			TopCode spot2 = markers.get(1);
			TopCode missingSpot;
			missingSpot = getMissingSpotInBlock3(spot1, spot2, isMissingSpotInTheMiddle(spot1, spot2));
			markers.add(missingSpot);
			block = new Block(3);
			block.setCenter(getMiddlePoint(markers));
			//TODO compute area and orientation of the block
		}else if(markers.size()==1){
			TopCode spot = markers.get(0);
			List<TopCode> missingSpots = getTwoMissingSpotsInBlock3(spot);
			markers.addAll(missingSpots);
			block = new Block(3);
			Point center = getMiddlePoint(missingSpots);
			block.setCenter(center);
		}else{
			//TODO error??? 
			Logger.error("weird number of markers detected for a block of type 2");
		}
		return block;
	}

	protected Block processBlockClass2(List<TopCode> markers, Block block) {
		if(markers.size()==2){
			TopCode marker1 = markers.get(0);
			TopCode marker2 = markers.get(1);
			float x = Math.min(marker1.getCenterX(),marker2.getCenterX())+Math.abs(marker1.getCenterX()-marker2.getCenterX())/2;
			float y = Math.min(marker1.getCenterY(),marker2.getCenterY())+Math.abs(marker1.getCenterY()-marker2.getCenterY())/2;
			block = new Block(2);
			block.setCenter(new Point(x,y));
			//TODO compute area and orientation of the block
		}else if(markers.size()==1){
			TopCode spot1 = markers.get(0);
			TopCode spot2 = getSingleMissingSpot(spot1);
			markers.add(spot2);
			/*To check both projected points uncomment this block and comment the two lines above
			 * List<TopCode> projected = getProjectedSpots(spot1);
			 * markers.addAll(projected);
			 */
			if(spot1 ==null || spot2==null){
				Logger.error("------------------------__FATAL ---------------------------");
			}
			Point center = getMiddlePoint(markers);
			block = new Block(2);
			block.setCenter(center);
		}else{
			//TODO error??? 
			Logger.error("weird number of markers detected for a block of type 2");
		}
		return block;
	}

	protected Block processBlockClass1(List<TopCode> markers) {
		Block block;
		block = new Block(1);
		TopCode marker = markers.get(0);
		Point center =  new Point(marker.getCenterX(), marker.getCenterY());
		block.setCenter(center);
		return block;
	}
	
	
	
	/**
	 * Return 2 missing points in a block of size 3
	 * There are 2 blocks not visibles and just one visible (the parameter)
	 * 
	 * Depending on the cache data the solution can be:    
	 *     |P1|spot|P2|
	 *     |spot|P1|P2|
	 *     |P1|P2|spot|
	 *     
	 * If there is no cache data for P1 and P2, it returns the solution 
	 * with spot in the center, i.e  |P1|spot|P2|
	 * @param spot visible spot
	 * @return
	 */
	private List<TopCode> getTwoMissingSpotsInBlock3(TopCode spot) {
		List<TopCode> missingSpots = new ArrayList<TopCode>();
		TopCode spot_e1=null, spot_e2=null;
		List<TopCode> projectedSpots =  getProjectedSpots(spot);
		if(!this.cacheEnabled){
			missingSpots=projectedSpots;
		}else{	//let's check in cache 
			TopCode spot1 = projectedSpots.get(0);
			TopCode spot2 = projectedSpots.get(1);
			ArrayList<TopCode> spotList = new ArrayList<TopCode>();
			spotList.add(spot1);
			TopCode p1 = getSpotFromCache(spotList);
			if(p1!=null){ //   we are in this case:       |?|P1|spot|?|
				spot_e1=p1;
				spotList.clear();
				spotList.add(spot2);
				TopCode p2 = getSpotFromCache(spotList); //check if we can also find the second spot in cache (right one)
				if(p2!=null){  //we are in this case:   |P1|spot|P2| and we found p1 and p2 in cache, we are done!
					spot_e2 = p2;
				}else{			//we didn't find P2 in the right side of spot, let's check if P2 is in the left side of P1, like this  |¿P2?|P1|spot
					Point deltas =  getDeltas(spot);
					p2 = getLeftProjectedSpot(p1, deltas.x, deltas.y);
					spotList.clear();
					spotList.add(p2);
					TopCode cached_p2 = getSpotFromCache(spotList);
					if(cached_p2!=null){		//we found p2 in the left side of p1 ---->  |P2|P1|spot|
						spot_e2 = cached_p2;
					}else{						//we couldn't find P2 anywhere. Let's use spot as central point
						spot_e2 = spot2;			//one spot visible, one spot cached and one spot dissapeared
					}
				}
			}else{								// we didn't find p1, let's check p2
				spotList.clear();
				spotList.add(spot2);
				spot_e2 = getSpotFromCache(spotList);
				if(spot_e2!=null){				//   we are in this case:       |spot|P2|?
					Point deltas =  getDeltas(spot);
					p1 = getRightProjectedSpot(spot_e2, deltas.x, deltas.y);
					spotList.clear();
					spotList.add(p1);
					TopCode cached_p1 = getSpotFromCache(spotList);
					if(cached_p1!=null){	//we found p1 in the right side of p2 ---->  |spot|P2|P1|
						spot_e1 = cached_p1;
					}else{					//we couldn't find P1 anywhere. Let's use spot as central point
						spot_e1=spot1;			//one spot visible, one spot cached and one spot dissapeared
					}
				}else{						//we didn't find neither p1 nor p2. Let's use spot as central point and the projectd points as p1 and p2
					spot_e1=spot1;
					spot_e2=spot2;
				}
			}
			missingSpots.add(spot_e1);
			missingSpots.add(spot_e2);
		}
		return missingSpots;
	}

	
	/**
	 * Return the missing spot in a block of size 3 with 2 visible spots (spot1 and spot2).
	 * if isInTheMiddle then the solution will be |spot1|P|spot2|
	 * Otherwise the possible solutions are: 
	 * 		|P1|spot1|spot2|
	 * 		|spot1|spot2|P2|
	 * @param spot1
	 * @param spot2
	 * @param isInTheMiddle
	 * @return
	 */
	private TopCode getMissingSpotInBlock3(TopCode spot1, TopCode spot2, boolean isInTheMiddle) {
		TopCode missingSpot = null;
		if(isInTheMiddle){
			missingSpot = getMissingMiddleSpot(spot1, spot2);
		}else{
			List<TopCode> outerSpots = getOuterProjectedSpots(spot1, spot2);
			TopCode spot_e = null;
			if(cacheEnabled){
				spot_e = getSpotFromCache(outerSpots);
			}
			if(spot_e!=null){
				missingSpot= spot_e;
			}else{
				missingSpot = outerSpots.get(0);
			}
		}
		return missingSpot;
	}

	
	private List<TopCode> getMissingSpotInBlock4(List<TopCode> spots){
		List<TopCode> spot_eList = new ArrayList<TopCode>();
		spots = TopCodeSorter.sortHorizontally(spots);
		if(spots.size()==3){
			TopCode spot1 = spots.get(0);
			TopCode spot2 = spots.get(1);
			TopCode spot3 = spots.get(2);
			if(isMissingSpotInTheMiddle(spot1, spot2)){
				spot_eList.add(getMissingMiddleSpot(spot1, spot2));
			}else if(isMissingSpotInTheMiddle(spot2, spot3)){
				spot_eList.add(getMissingMiddleSpot(spot2, spot3));
			}else{
				List<TopCode> outerPoints = getOuterProjectedSpots(spot1, spot3);				
				TopCode spot_e = getSpotFromCache(outerPoints);
				if(spot_e==null){ //we didn't find the spot in cache, let's return outerPoints.get(0) arbitrarily
					spot_e = outerPoints.get(0);
				}
				spot_eList.add(spot_e);
			}
		}else if(spots.size() == 2){
			TopCode spot1 = spots.get(0);
			TopCode spot2 = spots.get(1);
			if(!isMissingSpotInTheMiddle(spot1, spot2)){
				/*Posible configurations:  |s1|s2|p1|p2| , |p1|s1|s2|p2|, |p1|p2|s1|s2| */
				List<TopCode> outerSpots = getOuterProjectedSpots(spot1, spot2);
				TopCode leftProjectedSpot = outerSpots.get(0);
				TopCode rightProjectedSpot = outerSpots.get(1);
				TopCode p1 = getSpotFromCache(leftProjectedSpot);
				TopCode p2 = getSpotFromCache(rightProjectedSpot);
				if(p1!=null && p2!=null){ // |p1|s1|s2|p2|
					spot_eList.add(p1);
					spot_eList.add(p2);
				}else if(p1!=null){  //|?|p1|s1|s2|
					spot_eList.add(p1);  //let's try to find p2 on the left of p1
					Point deltas = getDeltas(p1);					
					p2 = getLeftProjectedSpot(p1, deltas.x, deltas.y);
					p2 = getSpotFromCache(p2);
					if(p2!=null){	//|p2|p1|s1|s2|
						spot_eList.add(p2);
					}else{			//We don't have data of p2. Let's use this configuration |p1|s1|s2|p2|
						spot_eList.add(spot2);
					}
				}else if(p2!=null){	//|s1|s2|p2|?|
					spot_eList.add(p2);
					Point deltas = getDeltas(p2);
					p1 = getRightProjectedSpot(p2, deltas.x, deltas.y);
					p1 = getSpotFromCache(p1);
					if(p1!=null){	//|s1|s2|p2|p1|
						spot_eList.add(p1);
					}else{			//We don't have data of p1. Let's use this configuration |p1|s1|s2|p2|
						spot_eList.add(spot1);
					}
				}else{ //weird, we don't have data of missing spots. Let's use this configuration |p1|s1|s2|p2|
					spot_eList.add(outerSpots.get(0));
					spot_eList.add(outerSpots.get(1));
				}
			}else{		//at least one of the missing spots is in the middle of the visible spots
				/*Posible configurations:	|s1|p1|p2|s2|, |s1|p1|s2|p2|, |p1|s1|p2|s2|*/
				if(areTwoMissingSpotInTheMiddle(spot1, spot2)){
					/* |s1|p1|p2|s2| */
					Point deltas = getDeltas(spot1);
					TopCode p1 = getRightProjectedSpot(spot1, deltas.x, deltas.y);
					List<TopCode> searchList = new ArrayList<TopCode>();
					searchList.add(p1);
					TopCode cached = getSpotFromCache(searchList);
					if(cached!=null){		//we found the point in cache
						spot_eList.add(cached);
					}else{
						spot_eList.add(p1);	//we didn't find the point in cache, let's use the projected point
					}
										
					deltas = getDeltas(spot2);
					TopCode p2 = getLeftProjectedSpot(spot2, deltas.x, deltas.y);
					searchList.clear();
					searchList.add(p2);
					cached = getSpotFromCache(searchList);
					if(cached!=null){		//we found the point in cache
						spot_eList.add(cached);
					}else{
						spot_eList.add(p2);	//we didn't find the point in cache, let's use the projected point
					}
				}else{
					/*	s1|p1|s2|p2|, |p1|s1|p2|s2|	*/
					Point deltasSpot1 = getDeltas(spot1);
					TopCode p1 = getRightProjectedSpot(spot1, deltasSpot1.x, deltasSpot1.y);
					List<TopCode> searchList = new ArrayList<TopCode>();
					searchList.add(p1);
					TopCode cached = getSpotFromCache(searchList);
					if(cached!=null){		//we found the point in cache
						// |?|s1|p1|s2|?|
						spot_eList.add(cached);
						//Test this config |s1|p1|s2|p2|
						Point deltasSpot2 = getDeltas(spot2);
						TopCode p2 = getRightProjectedSpot(spot2, deltasSpot2.x, deltasSpot2.y);
						searchList.clear();
						searchList.add(p2);
						TopCode cached2 = getSpotFromCache(searchList);
						if(cached2!=null){	//|s1|p1|s2|p2| we found p2 in cache
							spot_eList.add(cached2);
						}else{				
							//Test this config |p1|s1|p2|s2|
							p1 = getLeftProjectedSpot(spot1, deltasSpot1.x, deltasSpot1.y);
							TopCode cachedP1 = getSpotFromCache(p1);
							if(cachedP1!=null){
								spot_eList.add(cachedP1);
							}else{ //we couldn't find one of the spots, let's use |p1|s1|p2|s2| arbitrarily 
								spot_eList.add(p1);
							}
						}	
					}else{
						p1 = getLeftProjectedSpot(spot1, deltasSpot1.x, deltasSpot1.y);		//let's test p1 at the left of s1 |p1|s1|p2|s2|
						searchList.clear();
						searchList.add(p1);
						cached = getSpotFromCache(searchList);
						if(cached!=null){ 			//|p1|s1|p2|s2|
							spot_eList.add(cached);
						}else{
							spot_eList.add(p1);		//didn't find p1 in cache, we use the projected point
						}
						//p2 should be at the left side of s2
						deltasSpot1 = getDeltas(spot2);
						TopCode p2 = getLeftProjectedSpot(spot2, deltasSpot1.x, deltasSpot1.y);
						searchList.clear();
						searchList.add(p2);
						TopCode cached2 = getSpotFromCache(searchList);
						if(cached2!=null){	//|s1|p1|s2|p2| we found p2 in cache
							spot_eList.add(cached2);
						}else{				//didn't find p2 in cache, we use the projected point
							spot_eList.add(p2);
						}
					}
				}
			}
		}else if(spots.size() == 1){
			TopCode spot = spots.get(0);
			List<TopCode> projectedSpots = getProjectedSpots(spot);
			TopCode projected_leftSpot = projectedSpots.get(0);
			TopCode projected_rightSpot = projectedSpots.get(1);
			TopCode cachedLeft = getSpotFromCache(projected_leftSpot);
			TopCode cachedRight = getSpotFromCache(projected_rightSpot);
			if(cachedLeft!=null && cachedRight!=null){
				/*Posible configurations |p1|s1|p2|p3| - |p1|p2|s1|p3|*/
				spot_eList.add(cachedLeft);
				spot_eList.add(cachedRight);
				//let's test |?|p2|s1|p3|
				Point deltas = getDeltas(projected_leftSpot);
				TopCode leftleftSpot = getLeftProjectedSpot(projected_leftSpot,deltas.x, deltas.y);
				TopCode cached_leftleftSpot = getSpotFromCache(leftleftSpot);
				if(cached_leftleftSpot!=null){		//p1|p2|s1|p3|
					spot_eList.add(cached_leftleftSpot);	
				}else{
					//let's test |p1|s1|p2|?|
					deltas = getDeltas(cachedRight);
					TopCode rightrightSpot = getRightProjectedSpot(projected_rightSpot, deltas.x, deltas.y);
					TopCode cached_rightrightSpot = getSpotFromCache(rightrightSpot);
					if(cached_rightrightSpot!=null){	//|p1|s1|p2|p3|
						spot_eList.add(cached_rightrightSpot);
					}else{		//we didn't find p3, let's use |p1|s1|p2|p3| arbitrarily
						spot_eList.add(rightrightSpot);
					}
				}
			}else if(cachedLeft!=null){		// |?|?|p1|s1|
				spot_eList.add(cachedLeft);	//P1
				Point deltas_left1 = getDeltas(cachedLeft);
				TopCode projected_left1 = getLeftProjectedSpot(cachedLeft, deltas_left1.x, deltas_left1.y);
				TopCode cached_left1 = getSpotFromCache(projected_left1);
				Point deltas_left2 = getDeltas(projected_left1);
				TopCode projected_left2 = getLeftProjectedSpot(projected_left1, deltas_left2.x,deltas_left2.y);
				TopCode cached_left2 = getSpotFromCache(projected_left2);
				if(cached_left1!=null){		// |?|p2|p1|s1|
					spot_eList.add(cached_left1);
					if(cached_left2!=null){		// |p3|p2|p1|s1|
						spot_eList.add(cached_left2);
					}else{						// we didn't find the most left spot, let's use this configuration arbitrarily: |p2|p1|s1|p3| 
						spot_eList.add(projected_rightSpot);
					}
				}else if(cached_left2!=null){	// we are missing one spot: |p3|x|p1|s1| , let's use the projection
					spot_eList.add(cached_left2);
					spot_eList.add(projected_left1);
				}else{							// we didn't find two spots, let's use this configuration arbitrarily: |p2|p1|s1|p3|
					spot_eList.add(projected_left1);	//P2
					spot_eList.add(projected_rightSpot);			//P3
				}
			}else if(cachedRight!=null){	// |s1|p1|?|?|
				spot_eList.add(cachedRight);
				Point deltas_right1 = getDeltas(cachedRight);
				TopCode projected_right1 = getRightProjectedSpot(cachedRight, deltas_right1.x, deltas_right1.y);
				TopCode cached_right1 = getSpotFromCache(projected_right1);
				Point deltas_right2 = getDeltas(projected_right1);
				TopCode projected_right2 = getRightProjectedSpot(projected_right1, deltas_right2.x,deltas_right2.y);
				TopCode cached_right2 = getSpotFromCache(projected_right2);
				if(cached_right1!=null){	// |s1|p1|p2|?|
					spot_eList.add(cached_right1);
					if(cached_right2!=null){	//|s1|p1|p2|p3|
						spot_eList.add(cached_right2);
					}else{						// we didn't find the most right spot, let's use this configuration arbitrarily: |p3|s1|p1|p2|
						spot_eList.add(projected_leftSpot);
					}
				}else if(cached_right2!=null){	// we are missing one spot: |p1|s1|x|p3| , let's use the projection
					spot_eList.add(cached_right2);
					spot_eList.add(projected_right1);
				}else{				//we didn't find two spots, let's use this configuration arbitrarily: |p3|s1|p1|p2|
					spot_eList.add(projected_leftSpot);	//P3
					spot_eList.add(projected_right1);
				}
			}else{	//We didn't find adjacent spots --> |x|s1|x| , lets try the neighbors a and b : |a|x|s1|x|b|
				Point deltaLeft = getDeltas(projected_leftSpot);
				TopCode projected_leftleftSpot =  getLeftProjectedSpot(projected_leftSpot, deltaLeft.x,deltaLeft.y);
				TopCode cached_leftleftSpot = getSpotFromCache(projected_leftleftSpot);
				
				Point deltaRight = getDeltas(projected_rightSpot);
				TopCode projected_rightrightSpot = getRightProjectedSpot(projected_rightSpot, deltaRight.x, deltaRight.y);
				TopCode cache_rightrightSpot = getSpotFromCache(projected_rightrightSpot);
				
				if(cached_leftleftSpot!=null){	// |?|p1|x1|s1|x2| --> x1 = p2.  
					spot_eList.add(cached_leftleftSpot); //p1
					spot_eList.add(projected_leftSpot);	 // x1=p2
					
					Point deltasLeftLeft = getDeltas(cached_leftleftSpot);
					TopCode most_leftProjected = getLeftProjectedSpot(cached_leftleftSpot, deltasLeftLeft.x, deltasLeftLeft.y);
					TopCode cache_most_left= getSpotFromCache(most_leftProjected);
					if(cache_most_left!=null){	//we found p3=? --> |p3|p1|p2|s1|
						spot_eList.add(cache_most_left);
					}else{						//we didn't find p3 as the most left point. Let's use this configuration arbitrarily: |p1|p2|s1|p3|
						spot_eList.add(projected_rightSpot);
					}
				}else if(cache_rightrightSpot!=null){	//|x|x|s1|x1|p1| --> x1 = p2;
					spot_eList.add(cache_rightrightSpot);
					spot_eList.add(projected_rightSpot);
					
					Point deltasRightRight = getDeltas(cache_rightrightSpot);
					TopCode most_rightProjected = getRightProjectedSpot(cache_rightrightSpot, deltasRightRight.x, deltasRightRight.y);
					TopCode cached_mostRight = getSpotFromCache(most_rightProjected);
					if(cached_mostRight!=null){	// we found p3=? --> |s1|p2|p1|p3|
						spot_eList.add(cached_mostRight);
					}else{						//we didn't find p3 as the most right point. Let's use this configuration arbitrarily: |p3|s1|p2|p1|
						spot_eList.add(projected_leftSpot);	
					}
				}else{	//we didn't find any point in cache... let's use this configuration arbitrarily: |p1|p2|s1|p3|
					spot_eList.add(projected_leftSpot); //p2
					spot_eList.add(projected_rightSpot); //p3
					spot_eList.add(projected_leftleftSpot); //p1
				}
			}
		}
		return spot_eList;
	}
	
	
	private List<TopCode> getMissingSpotInBlock5(List<TopCode> spots){
		List<TopCode> spot_eList = new ArrayList<TopCode>();
		spots = TopCodeSorter.sortHorizontally(spots);
		if(spots.size()==4){
			TopCode spot1 = spots.get(0);
			TopCode spot2 = spots.get(1);
			TopCode spot3 = spots.get(2);
			TopCode spot4 = spots.get(3);
			if(isMissingSpotInTheMiddle(spot1, spot2)){		//|s1|P1|s2|s3|s4|
				spot_eList.add(getMissingMiddleSpot(spot1, spot2));
			}else if(isMissingSpotInTheMiddle(spot2, spot3)){	//|s1|s2|P1|s3|s4|
				spot_eList.add(getMissingMiddleSpot(spot2, spot3));
			}else if(isMissingSpotInTheMiddle(spot3, spot4)){	//|s1|s2|s3|P1|s4|
				spot_eList.add(getMissingMiddleSpot(spot3, spot4));
			}else{
				List<TopCode> outerProjected = getOuterProjectedSpots(spot1, spot4);
				TopCode cached = getSpotFromCache(outerProjected.get(0));  //try to find most left spot
				if(cached!=null){								//|P1|s1|s2|s3|s4|
					spot_eList.add(cached);	
				}else{
					cached = getSpotFromCache(outerProjected.get(1));  ////try to find most right spot
					if(cached!=null){
						spot_eList.add(cached);					//|s1|s2|s3|s4|P1|
					}else{	
						spot_eList.add(outerProjected.get(0));	//lets use most left projected point |P1|s1|s2|s3|s4|
					}
				}
			}
		}else if(spots.size()==3){
			TopCode spot1 = spots.get(0);
			TopCode spot2 = spots.get(1);
			TopCode spot3 = spots.get(2);
			spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, spot3));
		}else if(spots.size()==2){
			/*This can be improved, we are using a cached spot as "visible" but sometimes the position and orientation of the cached spots is not perfect*/
			TopCode spot1 = spots.get(0);
			TopCode spot2 = spots.get(1);
			spot_eList.addAll(getThreeMissingSpotsInBlock5EstimatingOne(spot1, spot2));
		}else if(spots.size()==1){
			/*This can be improved, we are using a cached spot as "visible" but sometimes the position and orientation of the cached spots is not perfect*/
			TopCode spot1 = spots.get(0);
			List<TopCode> projectedSpots = getProjectedSpots(spot1);
			TopCode cached = getSpotFromCache(projectedSpots.get(0));
			TopCode estimated;
			if(cached!=null){
				estimated=cached;
			}else{
				cached = getSpotFromCache(projectedSpots.get(1));
				if(cached!=null){
					estimated=cached;
				}else{
					estimated = projectedSpots.get(0);
				}
			}
			spot_eList.add(estimated);
			spot_eList.addAll(getThreeMissingSpotsInBlock5EstimatingOne(spot1,estimated));
		}
		return spot_eList;
	}

	/**
	 * 	 * Receives 2 visible spots of a block of type 5. Search one more spot in cache or use one of the projections
	 * and then computes the 2 missing spots
	 * @param spot1
	 * @param spot2
	 * @return
	 */
	private List<TopCode> getThreeMissingSpotsInBlock5EstimatingOne(TopCode spot1, TopCode spot2) {
		List<TopCode> spot_eList = new ArrayList<TopCode>();
		if(isMissingSpotInTheMiddle(spot1, spot2)){ //there is at least one spots in the middle, so at least we should find a cached projected spot
			List<TopCode> projectedSpots = getProjectedSpots(spot1);
			projectedSpots.addAll(getProjectedSpots(spot2));		
			TopCode cached = getSpotFromCache(projectedSpots);
			if(cached!=null){
				spot_eList.add(cached);
				spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, cached));
			}else{		//this case is weird, we will use one of the spots in the middle visible
				spot_eList.add(projectedSpots.get(1));
				spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, projectedSpots.get(1)));
			}
		}else{ //there isn't spot in the middle, so will search the outer projected spots
			List<TopCode> outerProjectedSpots = getOuterProjectedSpots(spot1, spot2);
			TopCode cached = getSpotFromCache(outerProjectedSpots);
			if(cached!=null){
				spot_eList.add(cached);
				spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, cached));
			}else{		// we didn't find the outer spots in cache, let's search the most outer spots: |?|x|s1|s2|x|?|
				TopCode left = outerProjectedSpots.get(0);
				TopCode right = outerProjectedSpots.get(1);
				Point deltas = getDeltas(left);
				TopCode mostLeftProjectedSpot = getLeftProjectedSpot(left, deltas.x, deltas.y);
				TopCode mostRightProjectedSpot = getRightProjectedSpot(right, deltas.x, deltas.y);
				cached = getSpotFromCache(mostLeftProjectedSpot);
				if(cached!=null){
					spot_eList.add(cached);
					spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, cached));
				}else{
					cached = getSpotFromCache(mostRightProjectedSpot);
					if(cached!=null){
						spot_eList.add(cached);
						spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, cached));
					}else{ //this case is weird, we will use the left outer projected spot as visible
						spot_eList.add(left);
						spot_eList.addAll(getTwoMissingSpotsInBlock5(spot1, spot2, left));
					}
				}					
			}
		}
		return spot_eList;
	}

	/**
	 * Receives 3 visible spots of a block of type 5 and returns the 2 missing spots
	 * @param s1
	 * @param s2
	 * @param s3
	 * @return 2 missing spots of a block of type 5
	 */
	private  List<TopCode> getTwoMissingSpotsInBlock5(TopCode s1, TopCode s2, TopCode s3) {
		List<TopCode> orderedSpotList = new ArrayList<TopCode>();
		orderedSpotList.add(s1);
		orderedSpotList.add(s2);
		orderedSpotList.add(s3);
		orderedSpotList = TopCodeSorter.sortHorizontally(orderedSpotList);
		TopCode spot1 = orderedSpotList.get(0);
		TopCode spot2 = orderedSpotList.get(1);
		TopCode spot3 = orderedSpotList.get(2);
		List<TopCode> spot_eList = new ArrayList<TopCode>();
		if(isMissingSpotInTheMiddle(spot1, spot2)){			//|?|s1|?|?|s2|?|s3|?
			if(areTwoMissingSpotInTheMiddle(spot1, spot2)){	//|s1|p1|p2|s2|s3|
				//case B1									//--|s1|p1|p2|s2|s3|--
				Point deltas = getDeltas(spot1);
				TopCode rightProjectedSpot = getRightProjectedSpot(spot1, deltas.x, deltas.y);
				TopCode cachedRight = getSpotFromCache(rightProjectedSpot);
				deltas = getDeltas(spot2);
				TopCode leftProjectedSpot = getLeftProjectedSpot(spot2, deltas.x, deltas.y);
				TopCode cachedLeft = getSpotFromCache(leftProjectedSpot);
				if(cachedRight!=null && cachedLeft!=null){	//B1-->  --|s1|p1|p2|s2|s3|--
					spot_eList.add(cachedRight); //p1
					spot_eList.add(cachedLeft);  //p2
				}else if(cachedRight!=null){  //didn't found p2, let's use the projected point
					spot_eList.add(cachedRight); //p1
					spot_eList.add(leftProjectedSpot); //p2
				}else if(cachedLeft!=null){//didn't found p1, let's use the projected point
					spot_eList.add(rightProjectedSpot); //p1
					spot_eList.add(cachedLeft); //p2
				}else{						//weird, we didn't find p1 and p2. Let's use the projections
					spot_eList.add(rightProjectedSpot);
					spot_eList.add(leftProjectedSpot);
				}
			}else{											//|?|s1|p1|s2|?|s3|?
				if(isMissingSpotInTheMiddle(spot2, spot3)){	//--|s1|p1|s2|p2|s3|--
					//case C
					Point deltas = getDeltas(spot1);
					TopCode rightProjectedSpot = getRightProjectedSpot(spot1, deltas.x, deltas.y);
					TopCode cachedRight = getSpotFromCache(rightProjectedSpot);
					
					deltas = getDeltas(spot2);
					TopCode rightProjectedSpot2 = getRightProjectedSpot(spot2, deltas.x, deltas.y);
					TopCode cachedRight2 = getSpotFromCache(rightProjectedSpot2);
					if(cachedRight!=null && cachedRight2!=null){	//--|s1|p1|s2|p2|s3|--
						spot_eList.add(cachedRight);
						spot_eList.add(cachedRight2);
					}else if(cachedRight!=null){			// we are missing this spot : |s1|p1|s2|x|s3|, let's use the projection
						spot_eList.add(cachedRight);
						spot_eList.add(rightProjectedSpot2);
					}else if(cachedRight2!=null){			// we are missing this spot : |s1|x|s2|p1|s3|, let's use the projection
						spot_eList.add(rightProjectedSpot);
						spot_eList.add(cachedRight2);
					}else{									//we are missing both spots |s1|x|s2|x|s3| but we know that between s1-s2 and s2-s3 
						spot_eList.add(rightProjectedSpot);	//there are spots. So, we will use the projections
						spot_eList.add(rightProjectedSpot2);
					}
				}else{
					//possible cases: 	D2 -> |p2|s1|p1|s2|s3| , D3-> |s1|p1|s2|s3|p2|
					List<TopCode> projectedSpots= getProjectedSpots(spot1);
					TopCode cachedLeft = getSpotFromCache(projectedSpots.get(0));
					TopCode cachedRight = getSpotFromCache(projectedSpots.get(1));
					if(cachedRight!=null){
						spot_eList.add(cachedRight);
					}else{											//we know that between spot1 and spot2 there is an spot, so we will use the projection 
						spot_eList.add(projectedSpots.get(1));		// if we didn't find it in cache
					}
					if(cachedLeft!=null){		//D2 -> |p2|s1|p1|s2|s3| 
						spot_eList.add(cachedLeft);
					}else{
						//let's test this config: D3-> |s1|p1|s2|s3|p2|
						Point deltas = getDeltas(spot3);
						TopCode mostRightProjection = getRightProjectedSpot(spot3, deltas.x, deltas.y);
						TopCode cachedMostRight = getSpotFromCache(mostRightProjection);
						if(cachedMostRight!=null){
							spot_eList.add(cachedMostRight); //D3-> |s1|p1|s2|s3|p2|
						}else{		//we are missing one spot, let's use this configuration arbitrarily |s1|p1|s2|s3|p2|
							spot_eList.add(mostRightProjection);
						}
					}
				}
			}
		}else if(isMissingSpotInTheMiddle(spot2, spot3)){	//|?|s1|s2|?|?|s3|?
			if(areTwoMissingSpotInTheMiddle(spot2, spot3)){	//|s1|s2|p1|p2|s3|
				//caso B2									//--|s1|s2|p1|p2|s3|--
				Point deltas = getDeltas(spot2);
				TopCode rightProjectedSpot = getRightProjectedSpot(spot2, deltas.x, deltas.y);
				TopCode cachedRight = getSpotFromCache(rightProjectedSpot);
				deltas = getDeltas(spot3);
				TopCode leftProjectedSpot = getLeftProjectedSpot(spot3, deltas.x, deltas.y);
				TopCode cachedLeft = getSpotFromCache(leftProjectedSpot);
				if(cachedRight!=null && cachedLeft!=null){		//--|s1|s2|p1|p2|s3|--
					spot_eList.add(cachedRight);
					spot_eList.add(cachedLeft);
				}else if(cachedRight!=null){ //we are missing this point --|s1|s2|p1|x|s3|--, let's use the projection
					spot_eList.add(cachedRight);
					spot_eList.add(leftProjectedSpot);
				}else if(cachedLeft!=null){ //we are missing this point --|s1|s2|x|p1|s3|--, let's use the projection
					spot_eList.add(rightProjectedSpot);
					spot_eList.add(cachedLeft);
				}else{					//weird, we didn't find p1 and p2. Let's use the projections
					spot_eList.add(rightProjectedSpot);
					spot_eList.add(leftProjectedSpot);
				}
			}else{	
				//possible cases: // D4-> |p2|s1|s2|p1|s3| , D1-> |s1|s2|p1|s3|p2|
				List<TopCode> projectedSpotsSpot3 = getProjectedSpots(spot3);
				TopCode leftCached = getSpotFromCache(projectedSpotsSpot3.get(0));
				TopCode rightCached = getSpotFromCache(projectedSpotsSpot3.get(1));
				if(leftCached!=null){
					spot_eList.add(leftCached);
				}else{															//we know that between spot2 and spot3 there is an spot, so we will use the projection 
					spot_eList.add(projectedSpotsSpot3.get(0));		// if we didn't find it in cache
				}
				if(rightCached!=null){
					spot_eList.add(rightCached);		//D1-> |s1|s2|p1|s3|p2|
				}else{
					//let's test D4-> |p2|s1|s2|p1|s3|
					Point deltas = getDeltas(spot1);
					TopCode mostLeftProjectedSpot = getLeftProjectedSpot(spot1, deltas.x,deltas.y);
					TopCode cachedMostLeftSpot = getSpotFromCache(mostLeftProjectedSpot);
					if(cachedMostLeftSpot!=null){
						spot_eList.add(cachedMostLeftSpot); // D4-> |p2|s1|s2|p1|s3|
					}else{
						spot_eList.add(mostLeftProjectedSpot);	//we are missing one spot, let's use this configuration arbitrarily |p2|s1|s2|p1|s3|
					}
				}
			}
		}else{											//no missing spots in the middle	
			//possible cases A1 -> |p1|p2|s1|s2|s3|, A2 -> |s1|s2|s3|p1|p2|, A3 -> |p1|s1|s2|s3|p2|
			Point deltas = getDeltas(spot1);
			TopCode leftProjected1 = getLeftProjectedSpot(spot1, deltas.x, deltas.y);
			TopCode cachedLeft = getSpotFromCache(leftProjected1);
			if(cachedLeft!=null){
				//possible cases: A1 -> |p1|p2|s1|s2|s3|,  A3 -> |p1|s1|s2|s3|p2|
				spot_eList.add(cachedLeft);
				deltas = getDeltas(spot3);
				TopCode rightProjected = getRightProjectedSpot(spot3, deltas.x, deltas.y);
				TopCode cachedRight = getSpotFromCache(rightProjected);
				if(cachedRight!=null){
					spot_eList.add(cachedRight);	//--|p1|s1|s2|s3|p2|--
				}else{
					//let's test A1 -> |p1|p2|s1|s2|s3|
					deltas = getDeltas(leftProjected1);
					TopCode leftProjected2 = getLeftProjectedSpot(leftProjected1, deltas.x, deltas.y);
					TopCode cachedLeft2 = getSpotFromCache(leftProjected2);
					if(cachedLeft2!=null){
						spot_eList.add(cachedLeft2);	//--|p1|p2|s1|s2|s3|--
					}else{					//weird, we are missing one spot. Let's use this configuration arbitrarily |p1|p2|s1|s2|s3|  
						spot_eList.add(leftProjected2);
					}
				}
			}else{			//it should be A2 -> |s1|s2|s3|p1|p2|
				deltas = getDeltas(spot3);
				TopCode rightProjected1 = getRightProjectedSpot(spot3, deltas.x, deltas.y);
				TopCode cachedRight1 = getSpotFromCache(rightProjected1);
				deltas = getDeltas(rightProjected1);
				TopCode rightProjected2 = getRightProjectedSpot(rightProjected1, deltas.x, deltas.y);
				TopCode cachedRight2 = getSpotFromCache(rightProjected2);
				if(cachedRight1!=null && cachedRight2!=null){
					spot_eList.add(cachedRight1);
					spot_eList.add(cachedRight2);
				}else if(cachedRight1!=null){
					//we are missing one spot. Let's use configuration arbitrarily |p1|s1|s2|s3|p2|
					spot_eList.add(cachedRight1);
					spot_eList.add(leftProjected1);
				}else if(cachedRight2!=null){					//we are missing this spot: |s1|s2|s3|x|p1| , let's assume that it is there anyway
					spot_eList.add(rightProjected1);
					spot_eList.add(cachedRight2);
				}else{										//weird, we didn't find p1 and p2. Let's use this configuration arbitrarily |p1|s1|s2|s3|p2|
					deltas = getDeltas(spot1);
					spot_eList.add(getLeftProjectedSpot(spot1, deltas.x,deltas.y));
					spot_eList.add(rightProjected1);
				}
			}
		}
		return spot_eList;
	}
	
	
	private Point getDeltas(TopCode spot, int number_of_spots){
		double hyp = interspot_distance*spot.unit*number_of_spots;
		double alpha = spot.orientation -HORIZONTAL_INITIAL_ROTATION_RADIANS; //Math.sin and Math.cos receives the angle in radians!!! 
		alpha = Math.abs(alpha);
		
		if(alpha>Math.PI/2){
			if(alpha>(Math.PI*3)/2){
				alpha = Math.abs(2*Math.PI-alpha);
			}else{
				alpha = Math.abs(Math.PI-alpha);
			}
		}
		double dy = hyp*Math.sin(alpha);
		double dx = hyp*Math.cos(alpha);
		Point ret = new Point(dx, dy);
		return ret;
	}
	
	private Point getDeltas(TopCode spot){
		return getDeltas(spot, 1);
	}
	
	/**
	 * Returns middle spot in the middle of spot1 and spot2.
	 * If there isn't a middle spot in cache, returns a  new spot with 
	 * center = middlePoint(spot1, spot2) and the same orientation of
	 * spot1 and spot2
	 * @param spot1
	 * @param spot2
	 * @return
	 */
	private TopCode getMissingMiddleSpot(TopCode spot1, TopCode spot2) {
		TopCode missingSpot;
		ArrayList<TopCode> spotList = new ArrayList<TopCode>();
		spotList.add(spot1);
		spotList.add(spot2);
		Point P1 = getMiddlePoint(spotList);
		TopCode middle = new TopCode(spot1.code);
		middle.setLocation((float)P1.x, (float)P1.y);
		middle.setDiameter(spot1.getDiameter());
		middle.setOrientation(spot1.orientation);
		if(cacheEnabled){
			Rect A1 = getProbableSurroundingArea(middle);
			TopCode cachedSpot = cache.isInCache(middle.code, A1);
			if(cachedSpot!=null){
				missingSpot = cachedSpot;
			}else{
				missingSpot = middle;
			}
		}else{
			missingSpot = middle;
		}
		return missingSpot;
	}

	protected Point getMiddlePoint(List<TopCode> markersList){
		Point res = new Point();
		List<Float> xlist = new ArrayList<Float>();
		List<Float> ylist = new ArrayList<Float>();
		for(Iterator<TopCode> iter = markersList.iterator();iter.hasNext();){
			TopCode marker = iter.next();
			xlist.add(Float.valueOf(marker.getCenterX()));
			ylist.add(Float.valueOf(marker.getCenterY()));
		}
		Object[] arrayx = (Object[])xlist.toArray();
		Object[] arrayy = (Object[])ylist.toArray();

		Arrays.sort(arrayx);
		Arrays.sort(arrayy);
		
		if(arrayx.length % 2 ==0){
			res.x = ((Float)arrayx[arrayx.length/2] + (Float)arrayx[arrayx.length/2 -1])/2; 
			res.y = ((Float)arrayy[arrayy.length/2] + (Float)arrayy[arrayy.length/2 -1])/2; 

		}else{
			res.x = (Float)arrayx[arrayx.length/2];
			res.y = (Float)arrayy[arrayy.length/2]; 
		}	
		return res;
	}
	

	/**
	 * Just for collections of size 3 or 5
	 * @param markersList
	 * @return
	 */
	protected TopCode getMarkerInTheMiddle(List<TopCode> markersList) {
		TopCode res = null;
		if(markersList.size()==3){
			TopCode marker1 = markersList.get(0);
			TopCode marker2 = markersList.get(1);
			TopCode marker3 = markersList.get(2);
			float arrayX[] = {marker1.getCenterX(),marker2.getCenterX(),marker3.getCenterX()};
			Arrays.sort(arrayX);
			float middleX = arrayX[1];
			if(marker1.getCenterX()==middleX){
				res=marker1;
			}else if(marker2.getCenterX()==middleX){
				res=marker2;
			}else if(marker3.getCenterX()==middleX){
				res=marker3;
			}
		}else if(markersList.size()==5){
			TopCode marker1 = markersList.get(0);
			TopCode marker2 = markersList.get(1);
			TopCode marker3 = markersList.get(2);
			TopCode marker4 = markersList.get(3);
			TopCode marker5 = markersList.get(4);
			float arrayX[] = {marker1.getCenterX(),marker2.getCenterX(),marker3.getCenterX(), marker4.getCenterX(),marker5.getCenterX()};
			Arrays.sort(arrayX);
			float middleX = arrayX[2];
			if(marker1.getCenterX()==middleX){
				res=marker1;
			}else if(marker2.getCenterX()==middleX){
				res=marker2;
			}else if(marker3.getCenterX()==middleX){
				res=marker3;
			}else if(marker4.getCenterX()==middleX){
				res=marker4;
			}else if(marker5.getCenterX()==middleX){
				res=marker5;
			}
		}
		return res;
	}
	
	
	/**
	 * Return the 2 points at left and right of spot1 and spot2. 
	 * 					|P1|spot1|spot2|P2|
	 * spot1 and spot2 does not have to be necesarilly consecutives points
	 * @param spot1
	 * @param spot2
	 * @return
	 */
	private List<TopCode> getOuterProjectedSpots(TopCode spot1, TopCode spot2){
		List<TopCode> res = new ArrayList<TopCode>();
		TopCode left, right; 
		if(topCodeComparator.compare(spot1, spot2)<0){
			left=spot1;
			right=spot2;
		}else{
			left=spot2;
			right=spot1;
		}
		
		Point deltas = getDeltas(left);
		TopCode P1 = getLeftProjectedSpot(left,deltas.x,deltas.y);
		deltas = getDeltas(right);
		TopCode P2 = getRightProjectedSpot(right,deltas.x,deltas.y);
		res.add(P1);
		res.add(P2);
		return res;
	}
	
	private TopCode getRightProjectedSpot(TopCode spot, double dx, double dy) {
		TopCode rightSpot = new TopCode(spot.code);
		rightSpot.x =(float)(spot.x + dx);
		double alpha = Math.abs(spot.orientation);
		int dy_multiplier = 1;
		if( (alpha<Math.PI/2) || (alpha>Math.PI && alpha< 3*Math.PI/2)  ){
			dy_multiplier = -1;
		}		
		rightSpot.y = (float)(spot.y + dy*dy_multiplier);
		rightSpot.orientation = spot.orientation;
		rightSpot.unit = spot.unit;
		return rightSpot;
	}

	private TopCode getLeftProjectedSpot(TopCode spot, double dx, double dy) {
		TopCode leftSpot = new TopCode(spot.code);
		leftSpot.x =(float)(spot.x - dx);
		double alpha = Math.abs(spot.orientation);
		int dy_multiplier = 1;
		
		if( (alpha>Math.PI/2 && alpha< Math.PI) || (alpha>3*Math.PI/2 && alpha< 2*Math.PI)  ){
			dy_multiplier = -1;
		}		
		leftSpot.y = (float)(spot.y + dy*dy_multiplier);
		leftSpot.orientation = spot.orientation;
		leftSpot.unit = spot.unit;
		return leftSpot;
	}

	/**
	 * Computes the 2 possible points given the position and orientation of spot
	 * The distance between 2 spots is fixed, so we can calculate where should be the next spot
	 * The return order is first the left point and then the right point
	 * @param spot
	 * @return
	 */
	private List<TopCode> getProjectedSpots(TopCode spot){
		List<TopCode> res = new ArrayList<TopCode>();;
		Point deltas = getDeltas(spot);		
		TopCode spot_1 = getLeftProjectedSpot(spot, deltas.x,deltas.y);
		TopCode spot_2 = getRightProjectedSpot(spot,deltas.x,deltas.y);
		
		res.add(spot_1);
		res.add(spot_2);

		return res;
	}
	
	
	/**
	 * Returns the missing spot. This function computes two projected spots
	 * and check the cache. If there is a spot in cache inside the probable area of
	 * one of the projected spots, then that spot is returned. Otherwise returns one of the 
	 * projected spots randomly.
	 * @param spot1
	 * @return
	 */
	private TopCode getSingleMissingSpot(TopCode spot){
		TopCode spot_e = null;
		List<TopCode> projectedSpots = getProjectedSpots(spot);
		TopCode spot1 = projectedSpots.get(0);
		if(cacheEnabled){	//FIXME do a for
			spot_e = getSpotFromCache(spot1);
			if(spot_e!=null){
				return spot1;
			}else{
				spot1 = projectedSpots.get(1);
				spot_e = getSpotFromCache(spot1);
				if(spot_e!=null){
					return spot1;
				}else{
					return projectedSpots.get(0);
				}
			}
		}else{
			return projectedSpots.get(0);
		}
	}
	
	
	/**
	 * Returns a square with center (spot.x,spot.y)
	 * and side PROBABLE_AREA_SIDE
	 * @param spot
	 * @return
	 */
	private Rect getProbableSurroundingArea(TopCode spot){
		return new Rect((int)spot.x-(TopCodeDetector.PROBABLE_AREA_SIDE/2), (int)spot.y-(TopCodeDetector.PROBABLE_AREA_SIDE/2), 
						TopCodeDetector.PROBABLE_AREA_SIDE, TopCodeDetector.PROBABLE_AREA_SIDE);
	}
	
	
	private double getDistance(TopCode spot1, TopCode spot2){
		return Math.hypot(spot1.x-spot2.x, spot1.y-spot2.y);
	}
	
	private boolean isMissingSpotInTheMiddle(TopCode spot1, TopCode spot2){
		double estimatedDistance = interspot_distance*spot1.unit;
		double realDistance = getDistance(spot1, spot2);
		return Math.abs(estimatedDistance-realDistance) > estimatedDistance/2; //50% of error tolerance in the estimation
	}
	
	private boolean areTwoMissingSpotInTheMiddle(TopCode spot1, TopCode spot2){
		double estimatedDistance = interspot_distance*spot1.unit;
		double realDistance = getDistance(spot1, spot2);
		return Math.abs(estimatedDistance-realDistance) > (estimatedDistance*3)/2; //50% of error tolerance in the estimation
	}
	
	
	private Point computeWidthAndHeightSinglemarkerBlock(Block block){
		double w=0,h=0;
		h = block.getSpots().get(0).getDiameter();
		w = h*block.getType();
		return new Point(w, h);
	}
		
	
	private Point computeWidthAndHeightMultimarkerBlock(Block block){
		List<TopCode> spots = block.getSpots();
		TopCode spot1;
		double distance;
		Point ret = new Point();
		double w=0, h=0;
		int size = spots.size();
		switch (size) {
		case 1:
			 w = spots.get(0).getDiameter();
			 h = w;
			break;
		case 2:
			spot1 =spots.get(0); 
			distance = getDistance(spot1, spots.get(1));
			w = (float)(distance + spot1.getDiameter());
			h = spot1.getDiameter();
			break;
		case 3:
			spots = TopCodeSorter.sortHorizontally(block.getSpots());
			spot1 =spots.get(0);
			distance = getDistance(spot1, spots.get(2));
			w = (float)(distance + spot1.getDiameter());
			h = spot1.getDiameter();
			break;
		case 4:
			spots = TopCodeSorter.sortHorizontally(block.getSpots());
			spot1 =spots.get(0);
			distance = getDistance(spot1, spots.get(3));
			w = (float)(distance + spot1.getDiameter());
			h= spot1.getDiameter();
			break;
		case 5:
			spots = TopCodeSorter.sortHorizontally(block.getSpots());
			spot1 =spots.get(0);
			distance = getDistance(spot1, spots.get(4));
			w = (float)(distance + spot1.getDiameter());
			h= spot1.getDiameter();
			break;
		default:
			break;
		}
		ret.x = w;
		ret.y = h;
		return ret;
	}
	
	
	private float computeBlockOrientation(Block block){
		float orientation = -1;
		if(block!=null){
			List<TopCode> spots = block.getSpots();
			if(spots!=null && spots.size()>0){
				return spots.get(0).orientation - HORIZONTAL_INITIAL_ROTATION_RADIANS;
			}
		}
		return orientation;
	}
	
	

	/**
	 * Search the spots in projectedSpots in cache and returns the first hit.
	 * If there is no spot in cache, returns null
	 * @param projectedSpots
	 * @return 
	 */
	private TopCode getSpotFromCache(List<TopCode> projectedSpots) {
		TopCode spotInCache = null;
		Rect area;
		for(Iterator<TopCode> iter = projectedSpots.iterator();(spotInCache==null)&&(iter.hasNext());){
			TopCode spot = iter.next();
			area = getProbableSurroundingArea(spot);
			spotInCache = this.cache.isInCache(spot.code, area);
		}
		return spotInCache;
	}
	
	/**
	 * Search the spots in projectedSpots in cache and returns the first hit.
	 * If there is no spot in cache, returns null
	 * @param projectedSpots
	 * @return 
	 */
	private TopCode getSpotFromCache(TopCode spot) {
		TopCode spotInCache = null;
		Rect area;
		area = getProbableSurroundingArea(spot);
		spotInCache = this.cache.isInCache(spot.code, area);
		return spotInCache;
	}
	
	
	public void enableCache(){
		this.cacheEnabled = true;
	}
}
