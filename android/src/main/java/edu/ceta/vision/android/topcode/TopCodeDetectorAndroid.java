package edu.ceta.vision.android.topcode;

import java.util.Set;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;
import edu.ceta.vision.core.blocks.Block;
import edu.ceta.vision.core.topcode.Scanner;
import edu.ceta.vision.core.topcode.TopCodeDetector;
import edu.ceta.vision.core.utils.Logger;

public class TopCodeDetectorAndroid extends TopCodeDetector {


	protected Bitmap bmp;
	
	public TopCodeDetectorAndroid(int max_markers, boolean probMode, int max_marker_diameter, 
								int size_cache, boolean cacheEnabled, boolean allow_different_spot_distance, 
								boolean use_native_scanner,boolean multiple_markers_per_block, Rect detectionZone){
		super(max_markers, probMode,size_cache, cacheEnabled, 
			allow_different_spot_distance, multiple_markers_per_block,detectionZone);
		if(use_native_scanner){
			this.scanner = new ScannerAndroidNative();
		}else{
			this.scanner = new ScannerAndroid();
		}
		if(max_marker_diameter>0){
			this.scanner.setMaxCodeDiameter(max_marker_diameter);
		}
	}
	
	private Mat cutImage(Mat rgbaImage){
		if(this.detectionZone!=null)
			return rgbaImage.submat(this.detectionZone);
		else
			return rgbaImage;
	}
	
	public Set<Block> detectBlocks(Mat rgbaImage){
		Logger.error("detectBlocks!");
		//Mat clone = rgbaImage.clone();
		Mat image = cutImage(rgbaImage);
		bmp = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(image, bmp);
		this.markers = ((ScannerAndroid)this.scanner).scan(this.bmp);
		
		
		/*----------------  PRUEBAS INT ARRAY
		//byte data[] = new byte[(int)rgbaImage.total()*rgbaImage.channels()];
		//rgbaImage.get(0, 0,data);
		
		/*ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
		DataInputStream in = new DataInputStream(inputStream);
		int dataInt[] = new int[(int)rgbaImage.total()];
		try{
			for(int i=0;i<dataInt.length;i++){
				dataInt[i] = in.readInt();
			}
		}catch(IOException e){
			e.printStackTrace();
		}
		
		this.markers = ((ScannerAndroid)this.scanner).scan(dataInt, rgbaImage.width(), rgbaImage.height());
		----------------------------   */

		/*------------- PRUEBAS SCANNER NATIVO ---------------*/
		/*Logger.error("$$$$$$$$ markers found JavaScanner = " + this.markers.size() + " $$$$$$$$$");
		ScannerAndroidNative nativeScanner = new ScannerAndroidNative();
		Mat grey = new Mat();
		Imgproc.cvtColor(clone, grey, Imgproc.COLOR_RGBA2GRAY);
		//nativeScanner.scan(dataInt, rgbaImage.width(), rgbaImage.height());
		nativeScanner.scanMat(grey.getNativeObjAddr());
		*/
		bmp.recycle();
		if(multiple_markers_per_block){
	        groupMarkers();
	        computeMultiMarkersBlocks();
		}else{
			computeSingleMarkersBlocks();
		}
        return this.blocks;
		
	}
	
	public Scanner getScanner(){
		return this.scanner;
	}
	
	public Mat getBinaryImage(){
		Bitmap preview = ((ScannerAndroid)this.scanner).getPreview();
		Mat previewMat = new Mat();
		Utils.bitmapToMat(preview, previewMat);
		
		return previewMat;
	}
}
