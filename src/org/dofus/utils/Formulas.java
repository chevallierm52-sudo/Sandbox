package org.dofus.utils;

import org.dofus.constants.EConstants;

public class Formulas {

	public static short getZaapCell(short mapId) {
		for(short[] zaap : EConstants.AMAKNA_ZAAPS)
			if(zaap[0] == mapId)
				return zaap[1];
		for(short[] zaap : EConstants.INCARNAM_ZAAPS)
			if(zaap[0] == mapId)
				return zaap[1];
		return -1;
	}

	public static boolean isZaapCell(int mapId, short cellId) {
		return getZaapCell((short) mapId) == cellId;
	}
	 
	public static short getZaapiCell(short mapId) {
		for(short[] zaapi : EConstants.BONTA_ZAAPI)
			if(zaapi[0] == mapId)
				return zaapi[1];
		for(short[] zaapi : EConstants.BRAKMAR_ZAAPI)
			if(zaapi[0] == mapId)
				return zaapi[1];
		return -1;
	}

	public static boolean isZaapiCell(int mapId, short cellId) {
		return getZaapiCell((short) mapId) == cellId;
	}

	public static boolean isWaypointCell(int mapId, short cellId) {
		return isZaapCell(mapId, cellId) || isZaapiCell(mapId, cellId);
	}
}
