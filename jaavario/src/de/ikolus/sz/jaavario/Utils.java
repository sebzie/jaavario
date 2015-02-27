package de.ikolus.sz.jaavario;

public class Utils {
	//using ICAO Standard Atmosphere
	public static double calculateHeight(float pressure) {
		return ((Math.pow( (pressure/1013.25) , 1/5.255) -1)*288.15) / -0.0065;
	}
	
}
