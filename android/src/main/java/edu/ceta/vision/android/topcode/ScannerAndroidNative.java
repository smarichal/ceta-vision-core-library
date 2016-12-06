/*
 * @(#) ScannerAndroid.java
 * 
 * Tangible Object Placement Codes (TopCodes)
 * Copyright (c) 2011 Michael S. Horn
 * 
 *           Michael S. Horn (michael.horn@tufts.edu)
 *           Northwestern University
 *           2120 Campus Drive
 *           Evanston, IL 60613
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2) as
 * published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package edu.ceta.vision.android.topcode;

import java.util.List;

import org.opencv.core.Mat;

import edu.ceta.vision.core.topcode.Scanner;
import edu.ceta.vision.core.topcode.TopCode;
import edu.ceta.vision.core.utils.Logger;
import android.graphics.Bitmap;

/**
 * Loads and scans images for TopCodes.  The algorithm does a single
 * sweep of an image (scanning one horizontal line at a time) looking
 * for a TopCode bullseye patterns.  If the pattern matches and the
 * black and white regions meet certain ratio constraints, then the
 * pixel is tested as the center of a candidate TopCode.
 *
 * @author Michael Horn
 */
public class ScannerAndroidNative extends Scanner {



   /** Binary view of the image */
   protected Bitmap preview;
   


/**
 * Default constructor
 */
   public ScannerAndroidNative() {
      this.w       = 0;
      this.h       = 0;
      this.data    = null;
      this.ccount  = 0;
      this.tcount  = 0;
      this.maxu    = 80;
      this.preview = null;
   }


/**
 * Scan the given image and return a list of all topcodes found in it.
 */
   public List<TopCode> scan(Bitmap image) {
	  this.preview = null;
      this.w       = image.getWidth();
      this.h       = image.getHeight();
      if (data == null || data.length < w * h) {
         this.data  = new int[w * h];
      }
      image.getPixels(this.data, 0, w, 0, 0, w, h);
      
      threshold();          // run the adaptive threshold filter
      return findCodes();   // scan for topcodes
   }
   
   /**
    * Scan the given image and return a list of all topcodes found in it.
    * smarichal: Added this method receiving the int array directly instead of the bitmap image
    */
   public List<TopCode> scan(int data[], int width, int height){
	   this.preview = null;
	   this.w=width;
	   this.h=height;
	   if (this.data == null || this.data.length < w * h) {
		   this.data  = new int[w * h];
       }
	   System.arraycopy(data, 0, this.data, 0, data.length);
	   
	   threshold();          // run the adaptive threshold filter
       return findCodes();   // scan for topcodes
   }

   public void scanMat(long rgbaImageAddress) {
//	   	TopCode[] spots = scanNativeMat(rgbaImage);
//		Logger.error("&&&&& Native spots found = "+spots.length+"&&&&&");
	   	int n = scanNativeMat2(rgbaImageAddress);
		Logger.error("&&&&& Native spots found = "+n+"&&&&&");
   }
  
   public native TopCode[] scanNativeMat(long image);
   public native int scanNativeMat2(long image);

   //public native List<TopCode> scanNative(int[] data, int width, int height);

  
  /**
   * For debugging purposes, create a black and white image that
   * shows the result of adaptive thresholding.
   */
   public Bitmap getPreview() {
	   if (this.preview != null) return preview;
	   this.preview = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

	   int pixel = 0;
	   int k = 0;
	   for (int j=0; j<h; j++) { 
		   for (int i=0; i<w; i++) {

			   pixel = (data[k++] >> 24);
			   if (pixel == 0) {
				   pixel = 0xFF000000;
			   } else if (pixel == 1) {
				   pixel = 0xFFFFFFFF;
			   } else if (pixel == 3) {
				   pixel = 0xFF00FF00;
			   } else if (pixel == 7) {
				   pixel = 0xFFFF0000;
			   }
			   this.preview.setPixel(i, j, pixel);
		   }
	   }
	   return preview;
   }



}
