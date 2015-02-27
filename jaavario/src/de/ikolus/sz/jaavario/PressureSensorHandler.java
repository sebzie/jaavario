package de.ikolus.sz.jaavario;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

public class PressureSensorHandler implements SensorEventListener {

	private long timeOfLastEvent=0;
	private float[] lastPressureReadings=new float[4];
	private long[] lastPressureReadingsTime=new long[4];
	private Handler uiHandler;
	private Lock lockForPressureBasedInformation;
	private Condition condForPbinfo;
	private PressureBasedInformation pbinfo;
	
	private float testPressure;
	
	public PressureSensorHandler(Handler uiHandler,Lock lockForPressureBasedInformation, Condition condition, PressureBasedInformation pbinfo) {
		this.uiHandler=uiHandler;
		this.lockForPressureBasedInformation=lockForPressureBasedInformation;
		this.condForPbinfo=condition;
		this.pbinfo=pbinfo;
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		newMeasurementReceived(event.values[0]);
		
	}

	public void newMeasurementReceived(float value) {
		timeOfLastEvent=SystemClock.elapsedRealtime();
		if(testPressure==0) {
			testPressure=value;
		}
		//TODO: here is room for improvement - sensor fusion with accelerometer could be a nice extension
		//the point of the following calculation is to smoothen the data - there are certainly more sophisticated ways for filtering and smoothening. For this program, however, this is good enough 
		
		//double climbSinkRate=(Utils.calculateHeight((value+lastPressureReadings[0])/2)-Utils.calculateHeight((lastPressureReadings[3]+lastPressureReadings[2]+lastPressureReadings[1])/3))*1000/(timeOfLastEvent-lastPressureReadingsTime[3]);
		
		//double climbSinkRate=((Utils.calculateHeight(value)-Utils.calculateHeight(lastPressureReadings[1]))*1000/(timeOfLastEvent-lastPressureReadingsTime[1])+
		//		(Utils.calculateHeight(lastPressureReadings[0])-Utils.calculateHeight(lastPressureReadings[2]))*1000/(lastPressureReadingsTime[0]-lastPressureReadingsTime[2])*0.5)/1.5;
			
		double climbSinkRate=((Utils.calculateHeight(value)-Utils.calculateHeight(lastPressureReadings[0]))*1000/(timeOfLastEvent-lastPressureReadingsTime[0])+
				(Utils.calculateHeight(value)-Utils.calculateHeight(lastPressureReadings[1]))*1000/(timeOfLastEvent-lastPressureReadingsTime[1])+
				(Utils.calculateHeight(value)-Utils.calculateHeight(lastPressureReadings[2]))*1000/(timeOfLastEvent-lastPressureReadingsTime[2])*0.5+
				(Utils.calculateHeight(lastPressureReadings[0])-Utils.calculateHeight(lastPressureReadings[2]))*1000/(lastPressureReadingsTime[0]-lastPressureReadingsTime[2])*0.5+
				(Utils.calculateHeight(lastPressureReadings[1])-Utils.calculateHeight(lastPressureReadings[3]))*1000/(lastPressureReadingsTime[1]-lastPressureReadingsTime[3])*0.25)/3.25;
				
		//double climbSinkRate=(Utils.calculateHeight(value)-testHeight)*1000/(timeOfLastEvent-lastPressureReadingsTime[1]);
		
		if(((value>lastPressureReadings[0] && lastPressureReadings[0]>lastPressureReadings[1]) ||
				(value<lastPressureReadings[0] && lastPressureReadings[0]<lastPressureReadings[1] ))
				&& Math.abs(testPressure-value)>0.08) { //the last three readings must either decrease or increase and the current pressure reading must be different from testPressure in a significant way -> 0.08 (this number has been chosen by trying around)
			//calculated climbSinkRate is relevant
		}
		else {//calculated climbSinkRate is not relevant
			climbSinkRate=0;
		}
		
		//Log.d("timing", ""+timeOfLastEvent+";"+value+" testPressure: "+testPressure+" climbSinkRate: "+climbSinkRate);
		
		lastPressureReadings[3]=lastPressureReadings[2];
		lastPressureReadings[2]=lastPressureReadings[1];
		lastPressureReadings[1]=lastPressureReadings[0];
		lastPressureReadings[0]=value;
		
		lastPressureReadingsTime[3]=lastPressureReadingsTime[2];
		lastPressureReadingsTime[2]=lastPressureReadingsTime[1];
		lastPressureReadingsTime[1]=lastPressureReadingsTime[0];
		lastPressureReadingsTime[0]=timeOfLastEvent;
		
		testPressure=testPressure*0.8f+value*0.2f;
		
		doMessaging(value,Utils.calculateHeight(value),climbSinkRate,timeOfLastEvent);
	}
	
	private void doMessaging(float pressure, double height, double climbSinkRate, long validTime) {
		//messaging to uithread
		if(uiHandler!=null) {
			PressureBasedInformation pbdata=new PressureBasedInformation();
			pbdata.setAll(pressure, height, climbSinkRate, validTime);
			Message toSend=uiHandler.obtainMessage(Constants.UI_MESSAGE_TYPE_COMPLEX, pbdata);
			toSend.sendToTarget();
		}
		
		//putting data into data structure that is read by noisemaker
		if(lockForPressureBasedInformation.tryLock()) {
			try {
				pbinfo.setAll(pressure, height, climbSinkRate, validTime);
			} finally {
				condForPbinfo.signalAll();
				lockForPressureBasedInformation.unlock();
			}
		}
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//nothing to do here?
		
	}
}