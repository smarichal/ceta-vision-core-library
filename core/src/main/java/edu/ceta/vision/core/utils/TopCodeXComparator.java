package edu.ceta.vision.core.utils;

import java.util.Comparator;

import edu.ceta.vision.core.topcode.TopCode;

public class TopCodeXComparator implements Comparator<TopCode>{
    @Override
    public int compare(TopCode spot1, TopCode spot2) {
    	return (int)(spot1.getCenterX() - spot2.getCenterX());
    }
}
