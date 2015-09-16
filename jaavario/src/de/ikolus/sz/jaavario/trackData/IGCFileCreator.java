package de.ikolus.sz.jaavario.trackData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.os.Environment;
import android.util.Log;


/**
 * 
 * This class builds an IGC file (see: http://carrier.csi.cam.ac.uk/forsterlewis/soaring/igc_file_format/igc_format_2008.html)
 * based on the data logged in PressureLogger and GPSLogger.
 *
 */
public class IGCFileCreator {

	public static void createFile(PressureLogger pressureLog, GPSLogger gpsLog, String filename) {
		ArrayList<PressureLoggerData> pData=pressureLog.getData();
		ArrayList<GPSLoggerData> gData=gpsLog.getData();
		if(pData.size()<=0 || gData.size()<=0) {
			Log.d("Logging", "No igc file was created as pressure or gps data was missing.");
			return;
		}
		
		
		PrintStream ps=null;		
		try {
			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename);
			
			int i=0;
			while(file.exists()) { //do not override existing file; TODO: maybe a give up condition should be added
				file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename+"#"+i);
			}
			
			ps=new PrintStream(new FileOutputStream(file),false);
			
		} catch (Exception e) {
			  e.printStackTrace();
		}
		
		
		//TODO: here header info could be printed to file
		
		int pos=0;
		
		for(GPSLoggerData gpsld : gData) {
			Date gpsDate=new Date(gpsld.date);
			
			PressureLoggerData pDataElement=pData.get(pos);
			double avg=0.0;
			int count=0;
			while(new Date(pDataElement.date).before(gpsDate) && pos<pData.size()) {
				avg+=pDataElement.height;
				count++;
				pos++;
				if(pos<pData.size()) {
					pDataElement=pData.get(pos);
				}
			}

			if(count>0) {
				avg=avg/count;
			}
			else {
				Log.d("Logging", "Pressure Data is missing - igc file not properly created.");
				avg=gpsld.altitude;
			}
			
			/*
			 * IGC file B record fix (see: http://carrier.csi.cam.ac.uk/forsterlewis/soaring/igc_file_format/igc_format_2008.html#link_4.1 ):
			 * timeutc: 6 bytes: HHMMSS
			 * Latitude: 8 bytes: DDMMmmmN/S (meaning 2*degree, 2*minutes, 3*decimal places of minutes, 1* either N or S)
			 * Longitude: 9 bytes: DDDMMmmmE/W
			 * Fix validity: 1 byte: A/V (A->3dFix V 2dfix)
			 * Press alt.: 5bytes: PPPPP (1013,25mbar...)
			 * GNSS alt.: 5bytes: GGGGG (WGS84 ellipsoid)
			 */
			
			Calendar cal=Calendar.getInstance();
			cal.setTime(gpsDate);
			ps.printf("B%02d%02d%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));

			long[] arr=coordinateValues(Math.abs(gpsld.latitude));
			ps.printf("%02d%02d%03d%c", arr[0], arr[1], arr[2],gpsld.latitude>=0?'N':'S');
			
			arr=coordinateValues(Math.abs(gpsld.longitude));
			ps.printf("%03d%02d%03d%c", arr[0], arr[1], arr[2],gpsld.longitude>=0?'E':'W');
			
			ps.printf("A%05d%05d%n", Math.round(avg),Math.round(gpsld.altitude));
			
		}
		
		try {
			
			if(ps.checkError()) { // does implicit flush
				Log.e("Logging", "Could not write igc log to file!");
			}
			ps.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	private static long[] coordinateValues(double val) {
		long deg=Math.round(Math.floor(val));
		double minutes=(val-deg)*60;
		long min=Math.round(Math.floor(minutes));
		long dec3pl=Math.round((minutes-min)*1000);
		long[] toReturn={deg,min,dec3pl};
		return toReturn;
	}
}
