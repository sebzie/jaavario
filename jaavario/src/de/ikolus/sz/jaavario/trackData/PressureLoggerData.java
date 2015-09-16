package de.ikolus.sz.jaavario.trackData;

public class PressureLoggerData {
	final float pressure;
	final double height;
	final double climbSinkRate;
	final long date;
	
	public PressureLoggerData(float pressure, double height, double climbSinkRate, long date) {
		this.pressure=pressure;
		this.height=height;
		this.climbSinkRate=climbSinkRate;
		this.date=date;
	}
	
}
