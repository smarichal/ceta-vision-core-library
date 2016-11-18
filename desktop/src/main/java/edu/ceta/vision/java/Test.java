package edu.ceta.vision.java;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;

import edu.ceta.vision.core.blocks.Block;
import edu.ceta.vision.core.topcode.TopCode;
import edu.ceta.vision.core.topcode.TopCodeDetectorDesktop;



public class Test extends JPanel implements KeyListener, WindowListener, MouseListener, MouseMotionListener, FilenameFilter {

	
	/** The main app window */
	   protected JFrame frame;
	  // protected ScannerDesktop scanner;
	   protected AffineTransform tform;
	   protected boolean binary;
	   protected boolean show_spots;
	   protected boolean annotate;
	   protected List spots;
	   protected Set<Block> markers;
	   protected int test_x;
	   protected int test_y;
	   protected int file_index;
	   protected String [] files;
	   private TopCodeDetectorDesktop detector;
	   private Mat img;
	   private String rootDirectory;
	   
	
	
	
	public Test(){
		super(true);
		setOpaque(true);
		setPreferredSize(new Dimension(1024, 768));
		addKeyListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);

		int max_markers = 40;
		boolean prob_mode = false;
		int max_marker_diameter = 200;
		int size_cache= 5;
		boolean cacheEnabled = false;
		boolean allow_different_spot_distance = false;
		detector = new TopCodeDetectorDesktop(max_markers, prob_mode,max_marker_diameter, size_cache, cacheEnabled, allow_different_spot_distance);
	
		 // create file list
		//this.rootDirectory = "/home/seba/phd/CETA/Code/image samples/blocks/";
		 this.rootDirectory = "/home/seba/phd/CETA/Code/image samples/blocks/4/3visibles/sequences/";
		 //this.rootDirectory = "/home/seba/phd/CETA/Code/image samples/blocks/3/320/";
		 //this.rootDirectory = "/home/seba/phd/CETA/Code/image samples/blocks/2/";
		this.file_index = 0;
		this.files = (new File(this.rootDirectory)).list(this);
		
		 if(rootDirectory.contains("sequences")){
			 this.detector.enableCache();
			 List<String> filesCol = new LinkedList<String>();
			 for(int i = 0;i<this.files.length;i++){
				 filesCol.add(this.files[i]);
			 }
			 
			 FileNameComparator fileNameComparator = new FileNameComparator();
			 Collections.sort(filesCol, fileNameComparator);
			 //Arrays.sort(this.files);
			 this.files = filesCol.toArray(this.files);
		 }
		 clear();

		 //--------------------------------------------------
		 //Create and set up the frame.
		 //--------------------------------------------------
		 this.frame = new JFrame("TopCode Debugger");
		 frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		 frame.setContentPane(this);
		 frame.addWindowListener(this);
		 frame.pack();
		 frame.setVisible(true);

		 
		 
		 requestFocusInWindow();
		 loadTest();
		
		 System.out.println("saliendo!");	
	}
	
	
	

	public class FileNameComparator implements Comparator<String>{
	
	    @Override
	    public int compare(String str1, String str2) {
	
	       // extract numeric portion out of the string and convert them to int
	       // and compare them, roughly something like this
	
	       int num1 = Integer.parseInt(str1.substring(0, str1.indexOf(".") ));
	       int num2 = Integer.parseInt(str2.substring(0, str2.indexOf(".") ));
	
	       return num1 - num2;
	
	    }
	}
	
	//-----------------------------------------------------------------
		// Load the next image from the test directory
		//-----------------------------------------------------------------
		public void load(String file, boolean fullPath) {
			clear();
			if (file == null) return;
			if(!fullPath){
				file = this.rootDirectory+file;
			}
			if((!(new File(file)).exists()) ){
				return;
			}

			img = Highgui.imread(file);	
		
			long start_t = System.currentTimeMillis();
			
			this.markers = this.detector.detectBlocks(file);
			//this.spots = scanner.scan(file);
			start_t = System.currentTimeMillis() - start_t;
			System.out.println("Found " + this.markers.size() + " blocks.");
			System.out.println(this.detector.getScanner().getCandidateCount() + " candidates.");
			System.out.println(this.detector.getScanner().getTestedCount() + " tested.");
			TopCode top;
			
			for (Iterator<Block> iter = this.markers.iterator();iter.hasNext();){
				Block block = iter.next();
				spots = block.getSpots();
				for (int i=0; i<spots.size(); i++) {
					top = (TopCode)spots.get(i);
					top.printBits(top.getCode());
				}
			}
			
//			for (int i=0; i<spots.size(); i++) {
//				top = (TopCode)spots.get(i);
//				top.printBits(top.getCode());
//			}
			System.out.println(start_t + "ms elapsed time.");
		}

		public void loadTest() {
			if (files == null || file_index >= files.length) return;
			System.out.println(files[file_index]);

			load(files[file_index], false);
		}
		
		

		public void clear() {
			this.tform      = new AffineTransform();
			this.spots      = null;
			this.markers = null;
			this.show_spots = true;
			this.binary     = false;
			this.annotate   = false;
			this.test_x     = -1;
			this.test_y     = -1;
			this.markers = null;
		}
	
		
		protected void paintComponent(Graphics graphics) {
		      Graphics2D g = (Graphics2D)graphics;

		      int w = getWidth();
		      int h = getHeight();
		      g.setColor(Color.WHITE);
		      g.fillRect(0, 0, w, h);

		      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
		                         RenderingHints.VALUE_ANTIALIAS_ON);

		      BufferedImage image;

		      if (this.binary) {
		         image = this.detector.getScanner().getPreview();
		      } else {
		         image = this.detector.getScanner().getImage();
		      }
		      
		      g.drawRenderedImage(image, this.tform);

		      g.transform(tform);

		      if(this.markers!=null){
			      for(Iterator<Block> iter = this.markers.iterator();iter.hasNext();){
				      Block block = iter.next();
				      this.spots = block.getSpots();
			    	  if (show_spots && spots != null) {
				         for (int i=0; i<spots.size(); i++) {
				            TopCode spot = (TopCode)spots.get(i);
				            spot.drawDesktop(g);
				            spot.drawDesktopCenter(g);
				            spot.drawDesktopOrientationRadians(g);
				         }
				      }
		
				      if (annotate && spots != null) {
				         for (int i=0; i<spots.size(); i++) {
				            TopCode spot = (TopCode)spots.get(i);
				            spot.annotate(g, this.detector.getScanner());
				         }
				      }
		
				      if (test_x >= 0 && test_y >= 0) {
				         TopCode spot = new TopCode();
				         spot.decode(this.detector.getScanner(), test_x, test_y);
				         if (spot.isValid()) {
				            spot.drawDesktop(g);
				         }
				         else {
				            spot.annotate(g, this.detector.getScanner());
				         }
				      } 
			     }
		    } 
		}
	
		
		public void pan(int dx, int dy) {
			double sx = this.tform.getScaleX();
			double sy = this.tform.getScaleY();
			this.tform.translate(dx / sx, dy / sy);
			repaint();
		}

		 
		public void zoom(double factor) {
			int w = getWidth();
			int h = getHeight();

			double dx = w/2.0;
			double dy = h/2.0;

			this.tform.preConcatenate(
					new AffineTransform().getTranslateInstance(-dx, -dy));
			this.tform.preConcatenate(
					new AffineTransform().getScaleInstance(factor, factor));
			this.tform.preConcatenate(
					new AffineTransform().getTranslateInstance(dx, dy));

			repaint();
		}
		
		
		
		
		
		/*---------------------------------------------------------------------------------------------*/
		/******************************************************************/
		/*                       KEYBOARD EVENTS                          */
		/******************************************************************/
		   public void keyPressed(KeyEvent e) {
		      int k = e.getKeyCode();
		      switch (k) {
		      // F10

		      case KeyEvent.VK_A:
		         this.annotate = !this.annotate;
		         repaint();
		         break;
		         
		      case KeyEvent.VK_B:
		         this.binary = !this.binary;
		         repaint();
		         break;

		      case KeyEvent.VK_T:
		         this.show_spots = !this.show_spots;
		         repaint();
		         break;

		      case KeyEvent.VK_O:
		         if (e.isControlDown()) {
		            int result;
		            JFileChooser chooser = new JFileChooser(new File(rootDirectory));
		            result = chooser.showOpenDialog(null);
		            if (result == JFileChooser.APPROVE_OPTION) {
		               load(chooser.getSelectedFile().getAbsolutePath(), true);
		            }
		         }
		         repaint();
		         break;

		      case KeyEvent.VK_MINUS:
		         zoom(0.95);
		         repaint();
		         break;

		      case KeyEvent.VK_EQUALS:
		         zoom(1/0.95);
		         repaint();
		         break;
		         
		      case KeyEvent.VK_PAGE_UP:
		         if (files != null && file_index < files.length) {
		            this.file_index++;
		         }
		         loadTest();
		         repaint();
		         break;

		      case KeyEvent.VK_PAGE_DOWN:
		         if (file_index > 0) {
		            file_index--;
		         }
		         loadTest();
		         repaint();
		         break;
		      }
		   }
		   
		   
		   
			

		   /*---------------------------------------------------------------------------------------------*/
		   /*WINDOW EVENTS*/
			 
			 

			 public void windowClosing(WindowEvent e) {
			      frame.setVisible(false);
			      frame.dispose();
			      System.exit(0);
			   }
			  

			   int mouseX;
			   int mouseY;
			   double point[] = new double[2];
			 
			   public void mousePressed(MouseEvent e) {
			      mouseX = e.getX();
			      mouseY = e.getY();
			      if (e.isControlDown()) {
			         point[0] = mouseX;
			         point[1] = mouseY;
			         try {
			            this.tform.inverseTransform(point, 0, point, 0, 1);
			            this.test_x = (int)Math.round(point[0]);
			            this.test_y = (int)Math.round(point[1]);
			         } catch (Exception x) { ; }
			         
			         repaint();
			      }
			   }
			   public void mouseDragged(MouseEvent e) {
			      pan(e.getX() - mouseX, e.getY() - mouseY);
			      mouseX = e.getX();
			      mouseY = e.getY();
			   }



			   public boolean accept(File dir, String name) {
			      return (name.toLowerCase().endsWith(".jpg"));
			   }

			   /*---------------------------------------------------------------------------------------------*/
			   
	
	
	public static void main(String[] args) {
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
		Mat image;
		image = Highgui.imread("/home/seba/phd/CETA/Code/image samples/topcodes/IMG_20161024_205310.jpg");	
		//TODO solucionar esto
		
		
		
		//--------------------------------------------------
		// Fix cursor flicker problem (sort of :( )
		//--------------------------------------------------
		System.setProperty("sun.java2d.noddraw", "");


		//--------------------------------------------------
		// Use standard Windows look and feel
		//--------------------------------------------------
		try { 
			UIManager.setLookAndFeel(
					UIManager.getSystemLookAndFeelClassName());
		} catch (Exception x) { ; }


		//--------------------------------------------------
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		//--------------------------------------------------
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new Test();
			}
		});
	      
	      
	      
	}


	@Override
	public void mouseMoved(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

}
