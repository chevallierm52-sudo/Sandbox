package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.MapsData;
import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.utils.Formulas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaypointParser {

	private static final Logger logger = LoggerFactory.getLogger(WaypointParser.class);

	private static final int ZAAP_COST  = 200;
	private static final int ZAAPI_COST = 20;

	/**
	 * WV (client) → server opens zaap panel.
	 * Client sends this automatically when the player steps on a zaap cell.
	 */
	public static void panelZaaps(Characters character, IoSession session) {
		if(character == null || session == null) return;
		character.setDisplacement(true);

		StringBuilder sb = new StringBuilder("WC");
		sb.append(character.getCurrentMap().getId()).append("|").append(character.getSaveMap());
		for(short[] zaap : EConstants.AMAKNA_ZAAPS) {
			sb.append("|");
			sb.append(zaap[0]).append(";").append(getZaapCost(character, zaap[0]));
		}
		for(short[] zaap : EConstants.INCARNAM_ZAAPS) {
			sb.append("|").append(zaap[0]).append(";").append(getZaapCost(character, zaap[0]));
		}
		session.write(sb.toString());
	}

	/**
	 * Wv (client) → server opens zaapi panel.
	 * Shows destinations based on character's alignment (1=Bonta, 2=Brakmar).
	 */
	public static void panelZaapis(Characters character, IoSession session) {
		if(character == null || session == null) return;
		short[][] zaapis;
		int alignment = character.getAlignmentType();
		if(alignment == 1)
			zaapis = EConstants.BONTA_ZAAPI;
		else if(alignment == 2)
			zaapis = EConstants.BRAKMAR_ZAAPI;
		else {
			session.write("Wv");
			return;
		}

		character.setDisplacement(true);

		StringBuilder sb = new StringBuilder("Wc");
		sb.append(character.getCurrentMap().getId()).append("|").append(character.getSaveMap());
		for(short[] zaapi : zaapis) {
			sb.append("|");
			sb.append(zaapi[0]).append(";").append(getZaapiCost(character, zaapi[0]));
		}
		session.write(sb.toString());
	}

	public static void saveZaap(Characters character, IoSession session) {
		if(character == null || character.getCurrentMap() == null || session == null) return;
		int currentMapId = character.getCurrentMap().getId();
		short zaapCell = Formulas.getZaapCell((short) currentMapId);
		if(zaapCell < 0) {
			session.write("BN");
			return;
		}

		character.setSaveMap(currentMapId);
		character.setSaveCell(zaapCell);
		CharactersData.update(character);
		session.write("Im024");
	}

	/**
	 * WU{mapId} (zaap) or Wu{mapId} (zaapi) from client.
	 * packet.charAt(1) == 'U' → zaap, lowercase 'u' → zaapi.
	 */
	public static void use(Characters character, IoSession session, GameClient client, String packet) {
		if(!character.isDisplacement()) return;

		int mapId = parseDestinationMap(packet);
		if(mapId < 0) {
			return;
		}

		MapTemplate map = MapsData.findById(mapId);
		if(map == null) return;

		boolean isZaap = (packet.charAt(1) == 'U');
		short cell;
		int cost;

		if(isZaap) {
			cell = Formulas.getZaapCell((short) mapId);
			if(cell < 0) {
				logger.warn("No zaap cell for map {}", mapId);
				return;
			}
			cost = getZaapCost(character, mapId);
			if(character.getKamas() < cost) {
				logger.debug("{} not enough kamas for zaap (has {}, needs {})",
					new Object[]{ character.getName(), character.getKamas(), cost });
				return;
			}
		} else {
			cell = Formulas.getZaapiCell((short) mapId);
			if(cell < 0) {
				logger.warn("No zaapi cell for map {}", mapId);
				return;
			}
			cost = getZaapiCost(character, mapId);
			if(character.getKamas() < cost) {
				logger.debug("{} not enough kamas for zaapi (has {}, needs {})",
					new Object[]{ character.getName(), character.getKamas(), cost });
				return;
			}
		}

		if(cost > 0) {
			character.setKamas(character.getKamas() - cost);
			session.write(Statistic.getStatisticsMessage(character));
		}
		character.setDisplacement(false);
		RolePlayMovement.teleport(client, map, getArrivalCell(map, cell));
		CharactersData.update(character);

		// Close the panel on client side
		session.write(isZaap ? "WV" : "Wv");

		logger.debug("{} used {} to map {} cell {}",
			new Object[]{ character.getName(), isZaap ? "zaap" : "zaapi", mapId, cell });
	}

	private static int getZaapCost(Characters character, int mapId) {
		if(character != null && character.getCurrentMap() != null
				&& character.getCurrentMap().getId() == mapId) return 0;
		return ZAAP_COST;
	}

	private static int getZaapiCost(Characters character, int mapId) {
		if(character != null && character.getCurrentMap() != null
				&& character.getCurrentMap().getId() == mapId) return 0;
		return ZAAPI_COST;
	}

	private static short getArrivalCell(MapTemplate map, short actionCell) {
		if(map == null) return actionCell;
		if(map.isValidActorCell(actionCell, true)) return actionCell;
		Short safeCell = map.findNearestValidActorCell(actionCell, true);
		return safeCell != null ? safeCell.shortValue() : actionCell;
	}

	private static int parseDestinationMap(String packet) {
		if(packet == null || packet.length() <= 2) return -1;
		String data = packet.substring(2);
		int end = 0;
		while(end < data.length() && Character.isDigit(data.charAt(end))) end++;
		if(end <= 0) return -1;
		try {
			return Integer.parseInt(data.substring(0, end));
		} catch(NumberFormatException e) {
			return -1;
		}
	}
}
