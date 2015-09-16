package de.ikolus.sz.jaavario.trackData;

public class GPSLoggerData {
	final double latitude;
	final double longitude;
	final double altitude;
	final long date;
	
	public GPSLoggerData(double latitude, double longitude, double altitude, long date) {
		this.latitude=latitude;
		this.longitude=longitude;
		this.altitude=altitude;
		this.date=date;
	}
}
