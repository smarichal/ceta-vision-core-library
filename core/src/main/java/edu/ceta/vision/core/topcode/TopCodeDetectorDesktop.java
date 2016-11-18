package edu.ceta.vision.core.topcode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

import edu.ceta.vision.core.blocks.Block;

public class TopCodeDetectorDesktop extends TopCodeDetector{

	private BufferedImage image;
	
	public TopCodeDetectorDesktop(int max_markers, boolean probMode,int max_marker_diameter, 
				int size_cache, boolean cacheEnabled,boolean allow_different_spot_distance) {
		super(max_markers, probMode, max_marker_diameter, size_cache, cacheEnabled, allow_different_spot_distance);
		this.scanner = new ScannerDesktop();
		if(max_marker_diameter>0){
			this.scanner.setMaxCodeDiameter(max_marker_diameter);
		}
	}
	

	public Set<Block> detectBlocks(BufferedImage rgbaImage) {
		this.image = rgbaImage;
		this.markers = ((ScannerDesktop)this.scanner).scan(image);
        groupMarkers();
        computeBlocks();
        return this.blocks;
	}
	
	public Set<Block> detectBlocks(String file){
		try {
			this.markers = ((ScannerDesktop)this.scanner).scan(file);
			groupMarkers();
	        computeBlocks();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this.blocks;
	}
	
	public ScannerDesktop getScanner(){
		return (ScannerDesktop)this.scanner;
	}

}
