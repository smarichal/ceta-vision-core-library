package edu.ceta.vision.core.topcode;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;

import edu.ceta.vision.core.blocks.Block;
import edu.ceta.vision.core.utils.ShowImage;

public class TopCodeDetectorDesktop extends TopCodeDetector{

	private BufferedImage image;
	
	private boolean busy, showdebug;
	private Object syncObject = new Object();
	
	private Mat frame;
	private VideoCapture capture;

	private ShowImage showImagePanel;
	
	public TopCodeDetectorDesktop(int max_markers, boolean probMode,int max_marker_diameter, 
				int size_cache, boolean cacheEnabled,boolean allow_different_spot_distance,
				boolean multiple_markers_per_block, Rect detectionZone, boolean captureFrames, boolean showDebugFrames) {
		super(max_markers, probMode, size_cache, cacheEnabled, 
			allow_different_spot_distance, multiple_markers_per_block, detectionZone);
		this.busy=false;
		this.showdebug = showDebugFrames;
		if(this.showdebug){
			this.showImagePanel = new ShowImage();
		}
		this.scanner = new ScannerDesktop();
		if(max_marker_diameter>0){
			this.scanner.setMaxCodeDiameter(max_marker_diameter);
		}
		if(captureFrames){
			frame = new Mat();
			capture = new VideoCapture(0);
			image = new BufferedImage(detectionZone.width, detectionZone.height, BufferedImage.TYPE_3BYTE_BGR);
		}
		
	}

	
	public boolean isBusy() {
		return busy;
	}

	public void setBusy(boolean busy) {
		synchronized (syncObject) {
			this.busy = busy;
		}
	}

	/**
	 * Captures a new image from the webcam and performs the block detection 
	 * @param rgbaImage
	 * @return
	 */
	public Set<Block> detectBlocks() {
		this.setBusy(true);
		if(capture!=null && capture.isOpened()){
			capture.read(frame);
			//Core.flip(frame, frame, 0);
			//Core.flip(frame, frame, 1);
			Core.flip(frame, frame, -1);
			BufferedImage rgbaImage = new BufferedImage((int)capture.get(Highgui.CV_CAP_PROP_FRAME_WIDTH), (int)capture.get(Highgui.CV_CAP_PROP_FRAME_HEIGHT), BufferedImage.TYPE_3BYTE_BGR);
			
			//this.image=rgbaImage.getSubimage(this.detectionZone.x, this.detectionZone.y, this.detectionZone.width-this.detectionZone.x, this.detectionZone.height-this.detectionZone.x);
			this.image=rgbaImage;
			byte[] data = ((DataBufferByte)this.image.getRaster().getDataBuffer()).getData();
			frame.get(0,0, data);

			if(this.showdebug){
				this.showImagePanel.setImage(image);
				//this.showImagePanel.repaint();
				this.showImagePanel.paint();
			}
			this.markers = ((ScannerDesktop)this.scanner).scan(this.image);
			System.out.println("##### Marcadores detectados = " + this.markers.size());
			
			if(multiple_markers_per_block){
		        groupMarkers();
		        computeMultiMarkersBlocks();
			}else{
				computeSingleMarkersBlocks();
			}
		}else{
			this.setBusy(false);
			throw new RuntimeException("Couldn't open video capture. Have you called the constructor with captureFrames=true?");
		}
		this.setBusy(false);
        return this.blocks;
	}
	
	/**
	 * Performs the block detection with the provided image
	 * @param rgbaImage
	 * @return
	 */
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
	
	public synchronized Set<Block> detectBlocks(String file){
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
