package de.ikolus.sz.jaavario;

import java.util.Date;

import de.ikolus.sz.jaavario.trackData.GPSLogger;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

/**
 * 
 * Currently the GPS location is only used to create flight tracks and log files for after the flight
 * during the flight this info is not accessed
 *
 */

public class GPSLocationHandler implements LocationListener {
	
	private GPSLogger glogger;
	
	public GPSLocationHandler(GPSLogger glogger) {
		this.glogger=glogger;
	}

	@Override
	public void onLocationChanged(Location location) {
		glogger.log(location.getLatitude(), location.getLongitude(), location.getAltitude(), new Date().getTime());
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

}
