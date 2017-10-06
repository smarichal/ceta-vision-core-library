package edu.ceta.vision.core.utils;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;


public class ShowImage extends Panel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	BufferedImage  image;
	private Graphics graphics;
	
	
	
	public ShowImage(String name){
		JFrame frame = new JFrame(name);
		frame.getContentPane().add(this);
		frame.setSize(500, 500);
		frame.setVisible(true);
	}
	
//	public ShowImage() {
//		try {
//			System.out.println("Enter image name\n");
//			BufferedReader bf=new BufferedReader(new 
//					InputStreamReader(System.in));
//			String imageName=bf.readLine();
//			File input = new File(imageName);
//			image = ImageIO.read(input);
//		} catch (IOException ie) {
//			System.out.println("Error:"+ie.getMessage());
//		}
//	}

	public void setImage(BufferedImage image){
		this.image = image;
	}
	
	public void paint(){
		this.repaint();
	}
	public void paint(Graphics g) {
		if(this.graphics ==null){
			this.graphics = g; 
		}
		System.out.println("painting--------");
		g.drawImage( image, 0, 0, null);
	}

//	static public void main(String args[]) throws
//	Exception {
//		JFrame frame = new JFrame("Display image");
//		Panel panel = new ShowImage();
//		frame.getContentPane().add(panel);
//		frame.setSize(500, 500);
//		frame.setVisible(true);
//	}
}