package edu.ceta.vision.core.utils;


public class BlocksMarkersMap {
	
	
	public static int[] block1 = {31, 61, 103, 179, 227, 271, 283, 355, 391, 453};
	public static int[] block2 = {93, 117, 185, 203, 793};
	public static int[] block3 = {563, 651, 361, 309};
	public static int[] block4 = {171, 555, 421};
	public static int[] block5 = {1173, 1189, 677};

	
	
	
	public static boolean belongsToBlockClass(int blockClass, int id){
		boolean res = false;
		switch (blockClass) {
		case 1:
			res = isInside(block1,id);
			break;
		case 2:
			res = isInside(block2,id);		
			break;
		case 3:
			res = isInside(block3,id);
			break;
		case 4:
			res = isInside(block4,id);
			break;
		case 5:
			res = isInside(block5,id);
			break;
		default:
			break;
		}
		return res;
	}
	
	
	
	public static boolean isInside(int[] blockMarkers, int id){
		boolean res = false;
		for(int i=0;(!res)&&(i<blockMarkers.length);i++){
			res = blockMarkers[i]==id;
		}
		return res;
	}
	
	
	
	

}
