package edu.ceta.vision.core.topcode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

import org.opencv.core.Rect;

import edu.ceta.vision.core.blocks.Block;

public class TopCodeDetectorDesktop extends TopCodeDetector{

	private BufferedImage image;
	
	public TopCodeDetectorDesktop(int max_markers, boolean probMode,int max_marker_diameter, 
				int size_cache, boolean cacheEnabled,boolean allow_different_spot_distance,
				boolean multiple_markers_per_block, Rect detectionZone) {
		super(max_markers, probMode, size_cache, cacheEnabled, 
			allow_different_spot_distance, multiple_markers_per_block, detectionZone);
		this.scanner = new ScannerDesktop();
		if(max_marker_diameter>0){
			this.scanner.setMaxCodeDiameter(max_marker_diameter);
		}
	}
	
	public Set<Block> detectBlocks(BufferedImage rgbaImage) {
		this.image=rgbaImage.getSubimage(this.detectionZone.x, this.detectionZone.y, this.detectionZone.width, this.detectionZone.height);
		this.markers = ((ScannerDesktop)this.scanner).scan(image);
		if(multiple_markers_per_block){
	        groupMarkers();
	        computeMultiMarkersBlocks();
		}else{
			computeSingleMarkersBlocks();
		}
        return this.blocks;
	}
	
	public Set<Block> detectBlocks(String file){
		try {
			this.markers = ((ScannerDesktop)this.scanner).scan(file);
			if(multiple_markers_per_block){
		        groupMarkers();
		        computeMultiMarkersBlocks();
			}else{
				computeSingleMarkersBlocks();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this.blocks;
	}
	
	public ScannerDesktop getScanner(){
		return (ScannerDesktop)this.scanner;
	}

}
