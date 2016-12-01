package edu.ceta.vision.core.utils;

import java.util.Comparator;

import edu.ceta.vision.core.topcode.TopCode;

public class TopCodeXComparator implements Comparator<TopCode>{
    
    /**Minimum difference to consider that 2 coordinates are different
	i.e spot1.x <> spot2.x <---> |spot1.x - spot2.x| > distance_tolerance */
    private static float distance_tolerance = 3;
    
    @Override
    public int compare(TopCode spot1, TopCode spot2) {
    	int ret = 0;
    	float diffX = spot1.getCenterX() - spot2.getCenterX();
    	if(Math.abs(diffX) >= distance_tolerance){
	    	if(diffX > 0 )
	    		ret =  1;
	    	else if(diffX < 0)
	    		ret =  -1;
		}else{
    		float diffY = spot1.getCenterY() - spot2.getCenterY();
    		if(Math.abs(diffY) >= distance_tolerance){
    			if(diffY>0)
    				ret = 1;
    			else if(diffY<0)
    				ret = -1;
			}
		}
		return ret;
    }
}
