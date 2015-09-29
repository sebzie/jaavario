package de.ikolus.sz.jaavario;

class PressureBasedInformation {
	private float pressure;
	private double height;
	private double climbSinkRate;
	private long validTime=0;

	public void setAll(float pressure, double height, double climbSinkRate, long validTime) {
		this.pressure=pressure;
		this.height=height;
		this.climbSinkRate=climbSinkRate;
		this.validTime=validTime;
	}
	
	public float getPressure() {
		return pressure;
	}

	public void setPressure(float pressure) {
		this.pressure = pressure;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}

	public long getValidTime() {
		return validTime;
	}

	public void setValidTime(long validTime) {
		this.validTime = validTime;
	}

	public double getClimbSinkRate() {
		return climbSinkRate;
	}

	public void setClimbSinkRate(double climbSinkRate) {
		this.climbSinkRate = climbSinkRate;
	}
}
