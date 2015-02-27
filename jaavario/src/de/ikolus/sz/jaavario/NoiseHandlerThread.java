package de.ikolus.sz.jaavario;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class NoiseHandlerThread implements Runnable {
	
	private PressureBasedInformation pbinfo;
	private Lock lockForPbinfo;
	private Condition condForPbinfo;
	private long lastTime=0;
	private NoiseMaker noiseMaker;
	
	public NoiseHandlerThread(Lock lock, Condition condition, PressureBasedInformation pbinfo,float notificationSinkRate, float notificationClimbRate) {
		this.lockForPbinfo=lock;
		this.pbinfo=pbinfo;
		this.condForPbinfo=condition;
		noiseMaker=new NoiseMaker(notificationSinkRate,notificationClimbRate);
	}
	
	@Override
	public void run() {	
		while(!Thread.interrupted()) {
			try {
				lockForPbinfo.lockInterruptibly();
				//theoretically relevant new data could already be present here.
				//This is only the case if either the NoiseMaker takes very long for processing or the PressureSensor is read very quickly
				condForPbinfo.await();
			} catch (InterruptedException e) {
				break;
			}
			
			if(pbinfo.getValidTime()==lastTime) {
				lockForPbinfo.unlock();
			}
			else {
				double csRate=pbinfo.getClimbSinkRate();
				lastTime=pbinfo.getValidTime();
				lockForPbinfo.unlock();
				//play the stuff
				noiseMaker.makeNoise(csRate, lastTime);
			}
		}
		noiseMaker.shutdownNoiseMaker();
		
	}
}
