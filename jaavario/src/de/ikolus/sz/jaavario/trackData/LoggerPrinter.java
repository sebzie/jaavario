package de.ikolus.sz.jaavario.trackData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import android.os.Environment;
import android.util.Log;

public class LoggerPrinter {

	public static PrintStream createAndStartFileInteraction(String filename) throws Exception {
		File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename);
		
		int i=0;
		while(file.exists()) { //do not override existing file; TODO: maybe a give up condition should be added
			file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename+"#"+i);
		}
		
		return new PrintStream(new FileOutputStream(file),false);
	}
	
	//true if error occurred
	public static boolean stopFileInteraction(PrintStream ps) {
		boolean error=ps.checkError(); // does implicit flush
		if(error) { 
			Log.e("Logging", "Could not write log to file!");
		}
		ps.close();
		return error;
	}	
}
