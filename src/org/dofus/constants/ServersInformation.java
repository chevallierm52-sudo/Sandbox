package org.dofus.constants;

public class ServersInformation {

	private static int requireSubscribe = 0;
	
	public enum Server {
		NULL(0),
		BOUNE(612);
		
		public int value;
		
		Server(int value) { 
			this.value = value;
		}
		
		public int get() {
			return value;
		}
	}
	
	public enum States {
		OFFLINE(0),
		ONLINE(1),
		SAVING(2);
		
		States(int value) { }
	}
	
	public enum Population {
		RECOMMENDED(0),
		AVERAGE(1),
		HIGH(2),
		LOW(3),
		FULL(4);
		
		Population(int value) { }
	}
	
	public enum Community {
		FRENCH(0),
		ENGLISH(1),
		INTERNATIONALE(2),
		GERMAN(3),
		SPANISH(4),
		RUSSIAN(5),
		BRAZILIAN(6),
		DUTCH(7),
		ITALIAN(8),
		JAPANESE(10),
		DEBUG(99);
		
		public int value;
	    Community(int value) {
	    	this.value = value;
	    }
	    
	    public int get() {
	    	return value;
	    }
	}
	
	public static int getRequireSubscribe() {
		return requireSubscribe;
	}

	public static void setRequireSubscribe(int requiredSubscribe) {
		requireSubscribe = requiredSubscribe;
	}
	
	public static String get() {
		return new StringBuilder()
				.append("AH")
				.append(getServerId()).append(";")
				.append(States.ONLINE.ordinal()).append(";")
				.append(Population.HIGH.ordinal()).append(";")
				.append(getRequireSubscribe())
					.toString();
	}
	
	public static int getServerId() {
		return Server.BOUNE.get();
	}
}
