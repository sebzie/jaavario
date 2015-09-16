package de.ikolus.sz.jaavario.trackData;

import java.io.PrintStream;
import java.util.ArrayList;

import android.util.Log;

public class PressureLogger {
	private ArrayList<PressureLoggerData> dataContainer;
	
	public PressureLogger() {
		dataContainer=new ArrayList<PressureLoggerData>(40000); //initial capacity - if there are 5 readings per second this should last over two hours
	}

	public void log(float pressure, double height, double climbSinkRate, long date) {
		dataContainer.add(new PressureLoggerData(pressure, height, climbSinkRate, date));
	}

	public ArrayList<PressureLoggerData> getData() {
		return dataContainer;
	}
	
	public void createLogFile(String filename) {
		try {
			PrintStream ps=LoggerPrinter.createAndStartFileInteraction(filename);
			for(PressureLoggerData pld : dataContainer) {
				ps.printf("%f:%f:%f:%d%n", pld.pressure, pld.height, pld.climbSinkRate, pld.date);
			}
			LoggerPrinter.stopFileInteraction(ps);
		} catch (Exception e) {
			Log.e("Logging", "PressureLogger file could not be created!");
			e.printStackTrace();
		}
		
	}
	
}
