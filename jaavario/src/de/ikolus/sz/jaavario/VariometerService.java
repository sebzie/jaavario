package de.ikolus.sz.jaavario;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class VariometerService extends Service {
	private HandlerThread pressureHandlerThread;
	private PressureSensorHandler pressureSensorHandler;
	private SensorManager senseman;
	
	private Thread noiseThread;
	
	private final IBinder binderForServiceUser=new VariometerServiceBinder(this);
	
	public void setValues(Handler uiHandler,float notificationSinkRate, float notificationClimbRate) {

		ReentrantLock lockForPressureBasedInformation=new ReentrantLock();
		Condition condition=lockForPressureBasedInformation.newCondition();
		PressureBasedInformation pbinfo=new PressureBasedInformation();		
		
		if(pressureSensorHandler!=null) { //the original activity is gone and a new activity was created
			//-> recreate the threads with possibly new data - this is easier than making the threads updateable
			removeThreads();
		}
		
		pressureSensorHandler=new PressureSensorHandler(uiHandler, lockForPressureBasedInformation, condition, pbinfo);
		HandlerThread pressureHandlerThread=new HandlerThread("PressureSensorHandlerThread");
		pressureHandlerThread.start();
		Sensor pressureSens=((SensorManager) this.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE);

		
		//FOR TESTING:
		//PressureTester ptest=new PressureTester(pressureSensorHandler);
		//ptest.parseAndCreateSignals("R10C25C24C23C22C28C28C39C49C44C43C43C13R10M20S20S30R10S30S30R10S30S30R10M200");
		//ptest.parseAndCreateSignals("R5C25C35C45C55C65C75C85C80C80C80C80C80C80C80C60C40C30C30C30C30C30S15S15S15S15S20S30S30S30S30S30S30S30C40C40C40S30S30R10");
		//ptest.parseAndCreateSignals("S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20C50C50S20S20S20S20S20S20S20S20C60C99C99S20S20S20S20S20S20");
		//ptest.parseAndCreateSignals("C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10");
		//comment the following line out for testing		
		senseman.registerListener(pressureSensorHandler, pressureSens, SensorManager.SENSOR_DELAY_FASTEST,new Handler(pressureHandlerThread.getLooper()));
		 
	    
	    
	    NoiseHandlerThread nht=new NoiseHandlerThread(lockForPressureBasedInformation,condition,pbinfo,notificationSinkRate,notificationClimbRate);
	    noiseThread=new Thread(nht);
	    //noiseThread.setUncaughtExceptionHandler(uncaughtExHandler);
	    noiseThread.start();
		
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		senseman=(SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		Log.d("service", "service onCreate");
		
		NotificationCompat.Builder notificationBuilder=new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.ic_notify_paraglider).setContentTitle("Variometer").setContentText("Variometer active");
		
		Intent makeActiveIntent=new Intent();
		makeActiveIntent.setClass(this, MainActivity.class);
		makeActiveIntent.setPackage("de.ikolus.sz.jaavario");
		makeActiveIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		
		PendingIntent pendIntent=PendingIntent.getActivity(this, 0, makeActiveIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		notificationBuilder.setContentIntent(pendIntent);
		
		
		Intent stopVariometerIntent=new Intent();
		stopVariometerIntent.setClass(this,MainActivity.class);
		stopVariometerIntent.setPackage("de.ikolus.sz.jaavario");
		stopVariometerIntent.putExtra(Constants.shutdownService, true);
		stopVariometerIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent penStopIntent=PendingIntent.getActivity(this, 1, stopVariometerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		notificationBuilder.addAction(R.drawable.ic_action_stop, "stop Variometer", penStopIntent);
		//careful - if there are other notifications the button/action might not be shown: http://stackoverflow.com/questions/18249871/android-notification-buttons-not-showing-up
		//careful! - multiple PendingIntents can be weird: PendingIntent.getActivity(this, ->this is needs to be different for each call<-, intent, flags)
		
		startForeground(1, notificationBuilder.build());		
		
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		return Service.START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.d("service", "onBind");
		return binderForServiceUser;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		Log.d("service", "onUnbind");
		return true;
	}
	
	@Override
	public void onRebind(Intent intent) {
		Log.d("service", "onRebind");
	}
	
	@Override
	public void onDestroy() {
		Log.d("service","in onDestroy");
		removeThreads();
		super.onDestroy();
	}
	
	private void removeThreads() {
		senseman.unregisterListener(pressureSensorHandler);
		if(pressureHandlerThread!=null) {
			pressureHandlerThread.quit();
			pressureHandlerThread=null;
		}
		
		if(noiseThread!=null) {
			noiseThread.interrupt();
		}
	}

}