package edu.ceta.vision.core.utils;


public class BlocksMarkersMap {
	
	
	public static int[] block1 = {103,47,55,59,61,79,87,91,93,369};
	public static int[] block2 = {31,107,109,115,117,121,143,151,155,391};
	public static int[] block3 = {157,167,171,173,179,181,185,199,203,395};
	public static int[] block4 = {205,211,213,217,227,229,233,241,271,397};
	public static int[] block5 = {279,283,285,295,299,301,307,309,313,403};
	
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
