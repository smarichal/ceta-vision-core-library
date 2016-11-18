package edu.ceta.vision.core.topcode;


/*
 * @(#) ScannerAndroid.java
 * 
 * Tangible Object Placement Codes (TopCodes)
 * Copyright (c) 2007 Michael S. Horn
 * 
 *           Michael S. Horn (michael.horn@tufts.edu)
 *           Tufts University Computer Science
 *           161 College Ave.
 *           Medford, MA 02155
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import java.util.List;


/**
 * Loads and scans images for TopCodes.  The algorithm does a single
 * sweep of an image (scanning one horizontal line at a time) looking
 * for a TopCode bullseye patterns.  If the pattern matches and the
 * black and white regions meet certain ratio constraints, then the
 * pixel is tested as the center of a candidate TopCode.
 *
 * @author Michael Horn
 * @version $Revision: 1.4 $, $Date: 2008/02/04 15:02:13 $
 */
public class ScannerDesktop extends Scanner {

   /** Original image */
   protected BufferedImage image;

   /** Binary view of the image */
   protected BufferedImage preview;

/**
 * Default constructor
 */
   public ScannerDesktop() {
      this.image   = null;
      this.w       = 0;
      this.h       = 0;
      this.data    = null;
      this.preview = null;
      this.ccount  = 0;
      this.tcount  = 0;
      this.maxu    = 80;
   }


/**
 * Scan the given image file and return a list of topcodes found in it.
 */
   public List<TopCode> scan(String filename) throws IOException {
      return scan(ImageIO.read(new File(filename)));
   }


/**
 * Scan the given image and return a list of all topcodes found in it.
 */
   public List<TopCode> scan(BufferedImage image) {
      this.image   = image;
      this.preview = null;
      this.w       = image.getWidth();
      this.h       = image.getHeight();
      this.data    = image.getRGB(0, 0, w, h, null, 0, w);
      
      threshold();          // run the adaptive threshold filter
      return findCodes();   // scan for topcodes
   }


/**
 * Scan the image and return a list of all topcodes found in it.
 *
 * @param rgb an array of pixel data in packed RGB format
 * @param width width of the image
 * @param height height of the image
 */
   public List<TopCode> scan(int [] rgb, int width, int height) {
      this.w       = width;
      this.h       = height;
      this.data    = rgb;
      this.preview = null;
      this.image   = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      this.image.setRGB(0, 0, w, h, rgb, 0, w);

      threshold();         // run the adaptive threshold filter
      return findCodes();  // scan for topcodes
   }
   

/**
 * Returns the original (unaltered) image   
 */
   public BufferedImage getImage() {
      return this.image;
   }

   
/**
 * For debugging purposes, create a black and white image that
 * shows the result of adaptive thresholding.
 */
   public BufferedImage getPreview() {
      if (this.preview != null) return preview;
      this.preview =
      new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

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
            this.preview.setRGB(i, j, pixel);
         }
      }
      return preview;
   }
}