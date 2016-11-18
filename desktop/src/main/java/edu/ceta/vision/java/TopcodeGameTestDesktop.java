package edu.ceta.vision.java;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import edu.ceta.vision.core.TopcodeGameTest;

public class TopcodeGameTestDesktop {
	public static void main (String[] args) {
//		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
//		new LwjglApplication(new TopcodeGameTest(), config);
		
		
		Test test = new Test();
		test.main(null);
	}
}
