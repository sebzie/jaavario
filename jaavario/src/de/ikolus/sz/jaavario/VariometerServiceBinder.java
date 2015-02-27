package de.ikolus.sz.jaavario;

import android.os.Binder;
import android.os.Handler;

public class VariometerServiceBinder extends Binder {

	private VariometerService varioService;
	
	public VariometerServiceBinder(VariometerService varioService) {
		super();
		this.varioService=varioService;
	}
	
	public void setServiceValues(Handler uiHandler,float notificationSinkRate, float notificationClimbRate) {
		this.varioService.setValues(uiHandler,notificationSinkRate,notificationClimbRate);
	}
	
}
