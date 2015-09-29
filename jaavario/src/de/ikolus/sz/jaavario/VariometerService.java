package de.ikolus.sz.jaavario;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.ikolus.sz.jaavario.trackData.GPSLogger;
import de.ikolus.sz.jaavario.trackData.IGCFileCreator;
import de.ikolus.sz.jaavario.trackData.PressureLogger;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class VariometerService extends Service {
	private HandlerThread pressureHandlerThread;
	private PressureSensorHandler pressureSensorHandler;
	private SensorManager senseman;
	
	private Thread noiseThread;
	
	private HandlerThread GPSLocationHandlerThread;
	private GPSLocationHandler GPSLocationHandler;
	private LocationManager locMan;
	
	private WakeLock wLock;
	
	private ReentrantLock lockForPressureBasedInformation;
	private Condition condition;
	private PressureBasedInformation pbinfo;
	
	private float notificationSinkRate;
	private float notificationClimbRate;
	
	
	private PressureLogger plogger=new PressureLogger();
	private GPSLogger glogger=new GPSLogger();
	
	private Messenger uiMessenger=null;
	private AtomicBoolean sendMessagesToActivity=new AtomicBoolean(false);
	
	
	//TODO: currently it is expected (but not enforced) that only one activity connects to this service
	
	private void createGPSHandlingThread() {
	    GPSLocationHandler=new GPSLocationHandler(glogger);
	    GPSLocationHandlerThread=new HandlerThread("GPSLocationHandlerThread");
	    GPSLocationHandlerThread.start();
	    
	    locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, GPSLocationHandler,GPSLocationHandlerThread.getLooper());
	}
	
	private void createPressureHandlingThread() {
		pressureSensorHandler=new PressureSensorHandler(uiMessenger, sendMessagesToActivity, lockForPressureBasedInformation, condition, pbinfo,plogger);
		pressureHandlerThread=new HandlerThread("PressureSensorHandlerThread");
		pressureHandlerThread.start();
		Sensor pressureSens=((SensorManager) this.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE);

		
		//FOR TESTING:
		//PressureTester ptest=new PressureTester(pressureSensorHandler);
		//ptest.parseAndCreateSignals("R10C25C24C23C22C28C28C39C49C44C43C43C13R10M20S20S30R10S30S30R10S30S30R10M200");
		//ptest.parseAndCreateSignals("R5C25C35C45C55C65C75C85C80C80C80C80C80C80C80C60C40C30C30C30C30C30S15S15S15S15S20S30S30S30S30S30S30S30C40C40C40S30S30R10");
		//ptest.parseAndCreateSignals("S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20S20C50C50S20S20S20S20S20S20S20S20C60C99C99S20S20S20S20S20S20");
		//ptest.parseAndCreateSignals("C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10C10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10S10");
		//ptest.parseAndCreateSignals("C10C10S15S15S15C10C10C10C10S15S15S15C10C10C10C10C10S15S15C10C10C10C10C10S45S15S15S15C10C10C10C10C10C10C10C10S15S15S15S35C10C10C10C10C10C10S15S10S10S10S15S10S10S10S10S15S10S10S10S10S10S10S10S15S10S10S10S10S15S10S10S10S10S10S10S10S10S15S10S10");
		//comment the following line out for testing		
		
		senseman.registerListener(pressureSensorHandler, pressureSens, SensorManager.SENSOR_DELAY_FASTEST,new Handler(pressureHandlerThread.getLooper()));		
	}
	
	private void createNoiseHandlingThread() {
	    NoiseHandlerThread nht=new NoiseHandlerThread(lockForPressureBasedInformation,condition,pbinfo,notificationSinkRate,notificationClimbRate);
	    noiseThread=new Thread(nht);
	    noiseThread.start();
	}
	
	private void removePressureHandlingThread() {
		senseman.unregisterListener(pressureSensorHandler);
		if(pressureHandlerThread!=null) {
			pressureHandlerThread.quit();
			pressureHandlerThread=null;
		}
	}
	
	private void removeGPSHandlingThread() {
		locMan.removeUpdates(GPSLocationHandler);
		if(GPSLocationHandlerThread!=null) {
			GPSLocationHandlerThread.quit();
			GPSLocationHandlerThread=null;
		}
	}
	
	private void removeNoiseHandlingThread() {
		if(noiseThread!=null) {
			noiseThread.interrupt();
			//noiseThread.join();  //TODO?
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		senseman=(SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		locMan=(LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		Log.d("service", "service onCreate");
		
		PowerManager powMan=(PowerManager)getSystemService(POWER_SERVICE);
		wLock=powMan.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jaavario-keepsensorsread");
		wLock.acquire();
		
		//initialize shared objects
		lockForPressureBasedInformation=new ReentrantLock();
		condition=lockForPressureBasedInformation.newCondition();
		pbinfo=new PressureBasedInformation();	
		
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
		
		if(uiMessenger!=null) {
			Log.e("status", "things already seem to be running - but they should not!");
		}
		
		if(intent.hasExtra(Constants.notificationClimbRate) && intent.hasExtra(Constants.notificationSinkRate) && intent.hasExtra(Constants.pressureReadingsReceiver)) {
			uiMessenger=(Messenger) intent.getParcelableExtra(Constants.pressureReadingsReceiver);
			notificationClimbRate=intent.getFloatExtra(Constants.notificationClimbRate, 1.5f);
			notificationSinkRate=intent.getFloatExtra(Constants.notificationSinkRate, 1.5f);

			createPressureHandlingThread();
		    
			createNoiseHandlingThread();

		    createGPSHandlingThread();
		    
		    sendMessagesToActivity.set(true);
		}
		else {  //intent does not contain the relevant data!
			Log.e("status", "intent incomplete!");
		}

		
		return null;  //no binder object required
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		Log.d("service", "onUnbind");
		sendMessagesToActivity.set(false);
		return true;
	}
	
	@Override
	public void onRebind(Intent intent) {  //service is already running and has been set up
		Log.d("service", "onRebind");
		
		if(intent.hasExtra(Constants.notificationClimbRate) && intent.hasExtra(Constants.notificationSinkRate) && intent.hasExtra(Constants.pressureReadingsReceiver)) {
			Messenger mes=(Messenger) intent.getParcelableExtra(Constants.pressureReadingsReceiver);
			float notiClimbRate=intent.getFloatExtra(Constants.notificationClimbRate, 1.5f);
			float notiSinkRate=intent.getFloatExtra(Constants.notificationSinkRate, 1.5f);
			
			//recreate the threads with new data if there is new data -> this is easier than making the threads updateable
			if(uiMessenger!=mes) {  //a different activity tries to connect
				Log.d("threading", "Need to restart pressure thread");
				removePressureHandlingThread();
				uiMessenger=mes;
				createPressureHandlingThread();
			}
			if(notificationSinkRate!=notiSinkRate || notificationClimbRate!=notiClimbRate) { //settings have been changed
				Log.d("threading", "Need to restart noise thread");
				removeNoiseHandlingThread();
				notificationClimbRate=notiClimbRate;
				notificationSinkRate=notiSinkRate;
				createNoiseHandlingThread();
			}
			
			sendMessagesToActivity.set(true);
			
		}
		else {  //intent does not contain the relevant data!
			Log.e("status", "intent incomplete!");
		}
		
	}
	
	@Override
	public void onDestroy() {
		Log.d("service","in onDestroy");
		try {
			Message message=Message.obtain();
			message.what=Constants.UI_MESSAGE_TYPE_WAIT_FOR_LOGGING;
			uiMessenger.send(message);
			
			removePressureHandlingThread();
			//save to access pressure log
			removeGPSHandlingThread();
			//save to access gps log

			plogger.createLogFile("jaavario-"+new Date().getTime());
			glogger.createLogFile("jaavarioGPS-"+new Date().getTime());
			IGCFileCreator.createFile(plogger, glogger, "IGCFLIGHT"+new Date().getTime()+".igc");
			message=Message.obtain();
			message.what=Constants.UI_MESSAGE_TYPE_STOP_WAITING_FOR_LOGGING;
			uiMessenger.send(message);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//Logging Data can be released now
		
		removeNoiseHandlingThread();

		if(wLock!=null) {
			wLock.release();
			wLock=null;
		}
		
		Log.d("service","raus onDestroy");
		super.onDestroy();
	}

}
