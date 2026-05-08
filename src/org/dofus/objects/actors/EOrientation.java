package org.dofus.objects.actors;

import java.util.HashMap;
import java.util.Map;

public enum EOrientation {
	
	EAST, SOUTH_EAST, 
	SOUTH, SOUTH_WEST, 
	WEST, NORTH_WEST, 
	NORTH, NORTH_EAST;

	private static final Map<Integer, EOrientation> values = new HashMap<>();

	static {
		for(EOrientation e : values())
			values.put(e.ordinal(), e);
	}
 
	public static EOrientation valueOf(int ordinal) {
		return values.get(ordinal);
	}
}
