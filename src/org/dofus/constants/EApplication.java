package org.dofus.constants;

public enum EApplication {
	
	APPLICATION_VERSION("3.17.37-a"),
	CONFIG_XML_FILE("config.xml"),
	DEBUG(false),
	
	DOFUS_VERSION("1.29.1"),
	SUBSCRIPTION_DURATION(3153600000000L);
	
	String sValue; boolean bValue; private long lValue;
	EApplication(String value) { setsValue(value); }
	EApplication(boolean value) { this.bValue = value; }
	EApplication(Long value) { this.setlValue(value); }
	
	public String getsValue() {
		return sValue;
	}
	
	public void setsValue(String value) {
		sValue = value;
	}
	public long getlValue() {
		return lValue;
	}
	public void setlValue(long value) {
		this.lValue = value;
	}
}
