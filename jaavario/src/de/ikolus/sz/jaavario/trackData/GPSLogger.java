package de.ikolus.sz.jaavario.trackData;

import java.io.PrintStream;
import java.util.ArrayList;

import android.util.Log;

public class GPSLogger {
	ArrayList<GPSLoggerData> dataContainer;
	
	public GPSLogger() {
		dataContainer=new ArrayList<GPSLoggerData>(8000); //initial capacity - if there is 1 reading per second this should last over two hours
	}
	
	public void log(double latitude, double longitude, double altitude, long date) {
		dataContainer.add(new GPSLoggerData(latitude, longitude, altitude, date));
	}
	
	public ArrayList<GPSLoggerData> getData() {
		return dataContainer;
	}
	
	public void createLogFile(String filename) {
		try {
			PrintStream ps=LoggerPrinter.createAndStartFileInteraction(filename);
			for(GPSLoggerData gld : dataContainer) {
				ps.printf("%f:%f:%f:%d%n", gld.latitude, gld.longitude, gld.altitude, gld.date);
			}
			LoggerPrinter.stopFileInteraction(ps);
		} catch (Exception e) {
			Log.e("Logging", "GPSLogger file could not be created!");
			e.printStackTrace();
		}
		
	}
}
