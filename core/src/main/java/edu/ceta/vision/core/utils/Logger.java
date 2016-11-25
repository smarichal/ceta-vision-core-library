package edu.ceta.vision.core.utils;

import java.util.logging.Level;

public class Logger {

	
	private static java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());
	public static void info(String msg){
		logger.log(Level.INFO, msg);
	}
	
	public static void warning(String msg){
		logger.log(Level.WARNING, msg);
	}
	
	public static void error(String msg){
		logger.log(Level.SEVERE, msg);
	}
	
}
