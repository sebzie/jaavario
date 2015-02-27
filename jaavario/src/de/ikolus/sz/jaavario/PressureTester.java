package de.ikolus.sz.jaavario;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.SystemClock;
import android.util.Log;

public class PressureTester {

	//private float pressure=900.0f; //pressure corresponding to height
	private float height=988.66f;
	private int msBetweenSignals=180;
	private long timeOfLastSignal=SystemClock.elapsedRealtime();
	private Random rand;
	
	PressureSensorHandler toTest=null;
	
	public PressureTester(PressureSensorHandler psh) {
		this.toTest=psh;
		this.rand=new Random();
	}

	
	/*
	 * The language that can be used in String command is of the following form:
	 * <start>::=<sequence-element><start> | empty
	 * <sequence-element>::=C<number> | R<number> | N<number> | S<number> | empty
	 * <number> is digits representing an element of the natural numbers.
	 * 
	 * The meaning:
	 * C means climb - the following number/10 is the climb rate in m/s
	 * S means sink - the following number/10 is the sink rate in m/s
	 * R means repeat - the previous pressure value is sent again * the number
	 * N means no move - the current pressure is maintained and noise is added * number
	 */
	class TestExecutionThread extends Thread {
		private String command;
		public TestExecutionThread(String command) {
			this.command=command;
		}
		
		@Override
		public void run() {
			Pattern p=Pattern.compile("[CRNS]\\d+");
			Matcher m=p.matcher(command);
			while(m.find()) {
				String action=m.group();
				int number=Integer.parseInt(action.substring(1));
				long timeDifference=SystemClock.elapsedRealtime()-timeOfLastSignal;
				timeOfLastSignal=SystemClock.elapsedRealtime();
				switch(action.charAt(0)) {
				case 'C':
					height+=(number*timeDifference)/10000.0f;
					if(height>5000) { //well it is getting ridiculous - this is for normal paragliding...
						//fail
					}
					toTest.newMeasurementReceived(calculatePressureFromHeight(height));
				break;
				
				case 'S':
					height-=(number*timeDifference)/10000.0f;
					if(height<0) { //again this is for paragliding and not diving...
						//fail
					}
					toTest.newMeasurementReceived(calculatePressureFromHeight(height));
					Log.d("sinkcheck", ""+height+"   "+calculatePressureFromHeight(height));
				break;
				
				case 'R':
					for(int i=number;i>1;i--) {
						toTest.newMeasurementReceived(calculatePressureFromHeight(height));
						SystemClock.sleep(msBetweenSignals);
					}
					timeOfLastSignal=SystemClock.elapsedRealtime();
					toTest.newMeasurementReceived(calculatePressureFromHeight(height)); //no sleep for the last signal
				break;
				
				case 'N':
					if(height<1) { //might get close to 0
						//fail
					}
					for(int i=number;i>1;i--) {
						toTest.newMeasurementReceived(calculatePressureFromHeight(height)+(rand.nextBoolean()?1:-1)*rand.nextFloat());
						SystemClock.sleep(msBetweenSignals);
					}
					timeOfLastSignal=SystemClock.elapsedRealtime();
					toTest.newMeasurementReceived(calculatePressureFromHeight(height)+(rand.nextBoolean()?1:-1)*rand.nextFloat());
					//no sleep for last signal
				break;
				
				default:
					//fail
				}
				
				SystemClock.sleep(msBetweenSignals);
			}
		}
	}
	

	public void parseAndCreateSignals(String command) {
		TestExecutionThread tet=new TestExecutionThread(command);
		tet.start();
	}
	
	float calculatePressureFromHeight(double height) {
		return (float) (1013.25*Math.pow((1-(0.0065*height)/288.15),5.255));
	}
}
