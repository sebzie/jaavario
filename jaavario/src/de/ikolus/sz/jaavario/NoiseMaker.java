package de.ikolus.sz.jaavario;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

public class NoiseMaker {
	
	private final int sampleRate=48000; //hz
	
	class ContinuousNoiseCommunicationObject {
		private long[] data=new long[2];
		
		public synchronized void setFrequencyAndTime(int frequency,long time) {
			data[0]=frequency;
			data[1]=time;
		}
		
		public synchronized long[] getFrequencyAndTime() {
			return data.clone();
		}
	}
	
	class ContinuousNoiseThread extends Thread {
		
		private int currentlyGeneratedForFreq=0;
		private int length;
		private final int buffSize=4000;
		private short[] buffer=new short[buffSize];
		private AudioTrack noise=new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffSize*2, AudioTrack.MODE_STREAM);
		private final int continuousNoiseDurationWithoutRefresh=250; //in ms. This is the duration the continuous noise is played if no further signal is received.
		
		/**
		 * This method fills the buffer with samples of a sawtooth curve. The value of the
		 * first and last element of the buffer is 0. 
		 * @param freq
		 * @param sampleRate
		 * @param buffer
		 * @return number of samples written to the buffer. -1 if buffer is too small
		 */
		private int buildSawtoothForFrequency(int freq,int sampleRate, short[] buffer) {
			int cycleLength=sampleRate/freq;
			int startPosition=cycleLength/2;
			
			int noOfCycles=buffer.length/cycleLength;
			if(noOfCycles<=0) {
				return -1;
			}
			
			int i;
			for(i=0;i<noOfCycles*cycleLength;++i) {
				buffer[i]=(short)(32767*(-2.0/Math.PI)*Math.atan(1/Math.tan((Math.PI * (i+startPosition) / (sampleRate))/(1.0/freq))));
			}
			
			return i;
			
		}
		
		@Override
		public void run() {
			while(!Thread.interrupted()) {
				long[] data=continuousNoiseCommunication.getFrequencyAndTime();
				//is there something to play? && is the time still right to play it?
				if(data[0]!=0 && SystemClock.elapsedRealtime()-data[1]<=continuousNoiseDurationWithoutRefresh) {
					if(data[0]!=currentlyGeneratedForFreq) {
						length=buildSawtoothForFrequency((int)data[0], sampleRate, buffer);
					}
					if(length!=-1) {
						try {
							noise.play();
							noise.write(buffer, 0, length);
							noise.stop();
						}
						catch(IllegalStateException ise) {
							noise=new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffSize*2, AudioTrack.MODE_STREAM);
						}
					}
					else { //fail
						Log.e("sound", "buildSawtoothForFrequency returned -1");
					}
				}
				else {
					SystemClock.sleep(20);
				}
			}
			noise.release();
		}
	}	
	
	private final int minBufferSize=4000; //in shorts, not bytes
	private int bufferSize;
	private AudioTrack singleNoise;
	private short samples[];
	
	private ContinuousNoiseCommunicationObject continuousNoiseCommunication=new ContinuousNoiseCommunicationObject();
	private ContinuousNoiseThread cnt;

	
	private final double speedThreshold=0.5; //in m/s - only when above the speedThreshold a noise is made.
		
	//the climb sound is a sine curve which is changed according to the climb rate.
	//the lowest frequency is climbStart and the highest frequency is climbStart+climbSoundWidth 
	private final int climbSoundWidth=1800; //hz
	private final double climbSteps=12.0; //for climb rates up to climbSteps a sound can be generated
	private final int climbStart=600; //hz
	
	//similar to variables above - only a sawtooth curve
	private final int sinkSoundWidth=300; //hz
	private final double sinkSteps=8.0; //for sink rates up to sinkSteps a sound can be generated
	private final int sinkStart=100; //hz
	
	
	
	private float notificationSinkRate; //in m/s
	private float notificationClimbRate; //in m/s
	
	public NoiseMaker(float notificationSinkRate, float notificationClimbRate) {
		this.notificationSinkRate=notificationSinkRate;
		this.notificationClimbRate=notificationClimbRate;
		
		bufferSize=AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    	bufferSize=Math.max(bufferSize/2, minBufferSize); //bufferSize/2 -> conversion from bytes to shorts
		
	    singleNoise=new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize*2, AudioTrack.MODE_STATIC);		
	    
	    samples=new short[bufferSize];
    	
    	final int freqOfSingleNoiseTone=1200;
    	
    	//the hull of a sound curve typically consists of four parts: attack, decay, sustain, release - here no decay is used
	    for(int i=0; i<bufferSize; ++i) {
    		if(i<(bufferSize)/10) { //attack part of the hull of the sound curve
    			samples[i]=(short)((i/((bufferSize)/10.))*32767*Math.sin((freqOfSingleNoiseTone) * 2 * Math.PI * i / (sampleRate)));
    		}
    		else if(i>(bufferSize)*(9./10)) { //release part of the hull of the sound curve
    			samples[i]=(short)((((bufferSize)-i)/((bufferSize)/10.))*32767*Math.sin((freqOfSingleNoiseTone) * 2 * Math.PI * i / (sampleRate)));
    		}
    		else { //sustain part of the hull of the sound curve
    			samples[i]=(short)(32767*Math.sin((freqOfSingleNoiseTone) * 2 * Math.PI * i / (sampleRate)));
    		}
    	}
	    singleNoise.write(samples, 0, bufferSize);
	    
	    cnt=new ContinuousNoiseThread();
	    cnt.start();

	}
	
	//TODO: this is essentially domain logic put into a class that is primarily supposed to play sounds.
	//if more domain logic is required - pull it out into an extra class
	public void makeNoise(double climbSinkRate, long lastTime) {
		if(climbSinkRate>this.notificationClimbRate) {
			Log.d("reason", "climbSinkRate>this.notificationClimbRate: "+ climbSinkRate);
			
			if(climbSinkRate-speedThreshold>=0) {
				makeSingleNoise((int)Math.round((Math.min(climbSinkRate,speedThreshold+climbSteps)-speedThreshold)*(climbSoundWidth/climbSteps)+climbStart));
			}
			continuousNoiseCommunication.setFrequencyAndTime(0, SystemClock.elapsedRealtime());
		}
		else if(climbSinkRate<this.notificationSinkRate) {
			Log.d("reason", "climbSinkRate<this.notificationSinkRate: "+ climbSinkRate+"   this.notificationSinkRate"+this.notificationSinkRate );
			double sinkRate=climbSinkRate*-1;
			if(sinkRate-speedThreshold>=0) {
				int frequency=(int)Math.round((Math.min(sinkRate,speedThreshold+sinkSteps)-speedThreshold)*(sinkSoundWidth/sinkSteps)+sinkStart);
				continuousNoiseCommunication.setFrequencyAndTime(frequency, SystemClock.elapsedRealtime());
			}
		}
		else {
			continuousNoiseCommunication.setFrequencyAndTime(0, SystemClock.elapsedRealtime());
		}
		
	}

	
	public void makeSingleNoise(int freq) {
		singleNoise.stop();
		int playbackRate=(int)Math.round(freq/1200.0*sampleRate);
		singleNoise.setPlaybackRate(playbackRate);
		singleNoise.setPlaybackHeadPosition(0);
		singleNoise.play();
		SystemClock.sleep(Math.max(0,(bufferSize/playbackRate)*1000-20)); //the singleNoise is meant to be non interruptible.
		//as singleNoise.play() is non blocking, it is necessary to wait for a certain time for the sound to be finished.
		//the time necessary is calculated by the formula above. The 20 ms that are subtracted
		//allows for further execution before the sound playback is/might be finished.
		//This is ok for most cases as further preparatory steps are required before the next sound can be played
		//and those will also take their time.
	}
	
	//method that shuts down stuff in noisemaker
	public void shutdownNoiseMaker() {
		cnt.interrupt();
		singleNoise.release();
	}

}
