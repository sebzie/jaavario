package de.ikolus.sz.jaavario;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

public class MainActivity extends Activity {
	
	private boolean varioActive=false;
	private boolean varioBound=false;
	private boolean broken=false;
	
	private Handler uiValueUpdateHandler;
	
	private TextView heightTV;
	private TextView pressureTV;
	private TextView climbSinkTV;
	private TextView climbSinkRateDisplayTV;
	private SeekBar lowerBound;
	private SeekBar upperBound;
	private Button startButton;
	
	private SensorManager senseman;
	private Sensor pressureSens;
	
	private HandlerThread simplePressureReader;
	
	private ServiceConnection varioServiceConnection;	
	
	//this method is called when the application cannot work e.g. the device has no appropriate sensors...
	private void appIsBroken(String reason) {
		broken=true;
		((Button)this.findViewById(R.id.start)).setClickable(false);
		Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if(intent.hasExtra(Constants.shutdownService) && intent.getBooleanExtra(Constants.shutdownService, false)) {
			//shutdown service
			stopUsingVariometerService();
		}
		super.onNewIntent(intent);
	}
	
	private void stopUsingVariometerService() {
		if(varioBound) {
			unbindService(varioServiceConnection);
			varioBound=false;
		}
		stopService(new Intent(MainActivity.this, VariometerService.class));
		varioActive=false;
		startButton.setText(R.string.startName);
		
		lowerBound.setEnabled(true);
		upperBound.setEnabled(true);
		
		climbSinkTV.setVisibility(View.INVISIBLE);
		climbSinkRateDisplayTV.setVisibility(View.INVISIBLE);	
		
		startSimplePressureSensorReader();
	}
	
	private void startUsingVariometerService() {
		stopSimplePressureSensorReader(); //stop the simple pressure reader
		
		lowerBound.setEnabled(false);
		upperBound.setEnabled(false);
		
		climbSinkTV.setVisibility(View.VISIBLE);
		climbSinkRateDisplayTV.setVisibility(View.VISIBLE);	
		
		startService(new Intent(MainActivity.this, VariometerService.class));

		varioActive=true;
		varioBound=bindService(new Intent(MainActivity.this, VariometerService.class), varioServiceConnection, Context.BIND_AUTO_CREATE);
		startButton.setText(R.string.stopName);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(Constants.varioServiceRunning, varioActive);
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		heightTV=(TextView) this.findViewById(R.id.height);
		pressureTV=(TextView) this.findViewById(R.id.pressure);
		climbSinkTV=(TextView) this.findViewById(R.id.climbSinkRate);
		climbSinkRateDisplayTV=(TextView) this.findViewById(R.id.climbSinkRateDisplay);
		
		senseman=(SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		if(null==senseman) {
			//terminate with error
			appIsBroken("No SensorService is present!");
		}
		pressureSens=((SensorManager) this.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE);
		if(null==pressureSens) {
			//terminate with error 
			appIsBroken("No pressure sensor could be found!");
		}
		
		
		final SharedPreferences sPrefs=this.getPreferences(Context.MODE_PRIVATE);
		
		lowerBound=(SeekBar)this.findViewById(R.id.noiseLowerBound);
		lowerBound.setMax(20);
		lowerBound.setProgress(sPrefs.getInt(Constants.lowerBoundPreferenceKey, 10));
		
		upperBound=(SeekBar)this.findViewById(R.id.noiseUpperBound);
		upperBound.setMax(20);
		upperBound.setProgress(sPrefs.getInt(Constants.upperBoundPreferenceKey, 10));
		
		final TextView lowerBoundText=(TextView)this.findViewById(R.id.sinkRateSignaling);
		lowerBoundText.setText((lowerBound.getProgress()/10.0f)+0.5f+" m/s");
		
		lowerBound.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				//nothing to do here
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				//nothing to do here
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				lowerBoundText.setText((seekBar.getProgress()/10.0f)+0.5f+" m/s");
				sPrefs.edit().putInt(Constants.lowerBoundPreferenceKey, seekBar.getProgress()).commit();
			}
		});
		
		final TextView upperBoundText=(TextView)this.findViewById(R.id.climbRateSignaling);
		upperBoundText.setText((upperBound.getProgress()/10.0f)+0.5f+" m/s");
		
		upperBound.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				//nothing to do here
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				//nothing to do here
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				upperBoundText.setText((seekBar.getProgress()/10.0f)+0.5f+" m/s");
				sPrefs.edit().putInt(Constants.upperBoundPreferenceKey, seekBar.getProgress()).commit();
			}
		});


		uiValueUpdateHandler=new Handler(Looper.getMainLooper()) {
			
			@Override
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case Constants.UI_MESSAGE_TYPE_SIMPLE:
					pressureTV.setText(""+msg.arg1/100.0);
					heightTV.setText(""+msg.arg2/100.0);
				break;
				
				case Constants.UI_MESSAGE_TYPE_COMPLEX:
					PressureBasedInformation pbinfo=(PressureBasedInformation)msg.obj;
					pressureTV.setText(""+pbinfo.getPressure());
					heightTV.setText(""+Math.round(pbinfo.getHeight()*100)/100.0f); //this way of rounding is good enough for this purpose
					climbSinkTV.setText(""+Math.round(pbinfo.getClimbSinkRate()*100)/100.0f);
				break;
				
				default:
					//fail!
					Log.e("message", "Message with unknown what value received");
				}
			}
		};
		
		
		startButton=(Button)MainActivity.this.findViewById(R.id.start);
		startButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(!broken) {
					if(varioActive) { //the user wants to deactivate the vario
						stopUsingVariometerService();
					}
					else { //the user wants to activate the vario
						startUsingVariometerService();
					}
				}
			}
		});
		
		varioServiceConnection=new ServiceConnection() {
			
			@Override
			public void onServiceDisconnected(ComponentName name) {
				varioBound=false;
				
			}
			
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				VariometerServiceBinder binder=(VariometerServiceBinder)service;
				binder.setServiceValues(uiValueUpdateHandler, sPrefs.getInt(Constants.lowerBoundPreferenceKey, 10)*-0.1f-0.5f, sPrefs.getInt(Constants.upperBoundPreferenceKey, 10)*0.1f+0.5f);
				varioBound=true;
			}
		};
		
		//handle recreating of activity
		if(savedInstanceState!=null && savedInstanceState.getBoolean(Constants.varioServiceRunning, false)) {
			startUsingVariometerService();
		}
		else {
			climbSinkTV.setVisibility(View.INVISIBLE);
			climbSinkRateDisplayTV.setVisibility(View.INVISIBLE);
		}
		
	}
	
	private void startSimplePressureSensorReader() {
		if(simplePressureReader==null) {
			simplePressureReader=new HandlerThread("simplePressureSensorReader");
			simplePressureReader.start();
		}
		Handler hand=new Handler(simplePressureReader.getLooper());	
		senseman.registerListener(senseListener, pressureSens, SensorManager.SENSOR_DELAY_UI,hand);
	}
	
	private void stopSimplePressureSensorReader() {
		SensorManager senseman=(SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		senseman.unregisterListener(senseListener);
		if(simplePressureReader!=null) {
			simplePressureReader.quit();
			simplePressureReader=null;
		}
	}
	
	//a simple implementation that sends nearly unprocessed sensor data to the uihandler
	private SensorEventListener senseListener=new SensorEventListener() {
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			MainActivity.this.uiValueUpdateHandler.obtainMessage(
					Constants.UI_MESSAGE_TYPE_SIMPLE, Math.round(event.values[0]*100), (int)Math.round(Utils.calculateHeight(event.values[0])*100))
					.sendToTarget();
		}
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			//nothing to do here?
			
		}
	};
	
	@Override
	protected void onStart() {
		super.onStart();
		if(!broken) {
			if(varioActive) {
				if(!varioBound) {
					varioBound=bindService(new Intent(MainActivity.this, VariometerService.class), varioServiceConnection, Context.BIND_AUTO_CREATE);
				}
			}
			else {
				startSimplePressureSensorReader();
			}
		}
	}
	
	@Override
	protected void onStop() {
		if(varioActive) {
			if(varioBound) {
				unbindService(varioServiceConnection);
				varioBound=false;
			}
		}
		else {
			stopSimplePressureSensorReader();
		}
		super.onStop();

	}


}
