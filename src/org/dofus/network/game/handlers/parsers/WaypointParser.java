package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
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

	/**
	 * WV (client) → server opens zaap panel.
	 * Client sends this automatically when the player steps on a zaap cell.
	 */
	public static void panelZaaps(Characters character, IoSession session) {
		character.setDisplacement(true);

		StringBuilder sb = new StringBuilder("WC");
		boolean first = true;
		for(short[] zaap : EConstants.AMAKNA_ZAAPS) {
			if(!first) sb.append("|");
			sb.append(zaap[0]);
			first = false;
		}
		for(short[] zaap : EConstants.INCARNAM_ZAAPS) {
			sb.append("|").append(zaap[0]);
		}
		session.write(sb.toString());
	}

	/**
	 * Wv (client) → server opens zaapi panel.
	 * Shows destinations based on character's alignment (1=Bonta, 2=Brakmar).
	 */
	public static void panelZaapis(Characters character, IoSession session) {
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
		boolean first = true;
		for(short[] zaapi : zaapis) {
			if(!first) sb.append("|");
			sb.append(zaapi[0]);
			first = false;
		}
		session.write(sb.toString());
	}

	/**
	 * WU{mapId} (zaap) or Wu{mapId} (zaapi) from client.
	 * packet.charAt(1) == 'U' → zaap, lowercase 'u' → zaapi.
	 */
	public static void use(Characters character, IoSession session, GameClient client, String packet) {
		if(!character.isDisplacement()) return;

		int mapId;
		try {
			mapId = Integer.parseInt(packet.substring(2));
		} catch(NumberFormatException e) {
			return;
		}

		MapTemplate map = MapsData.findById(mapId);
		if(map == null) return;

		boolean isZaap = (packet.charAt(1) == 'U');
		short cell;

		if(isZaap) {
			cell = Formulas.getZaapCell((short) mapId);
			if(cell < 0) {
				logger.warn("No zaap cell for map {}", mapId);
				return;
			}
			if(character.getKamas() < ZAAP_COST) {
				logger.debug("{} not enough kamas for zaap (has {}, needs {})",
					new Object[]{ character.getName(), character.getKamas(), ZAAP_COST });
				return;
			}
			character.setKamas(character.getKamas() - ZAAP_COST);
			session.write(Statistic.getStatisticsMessage(character));
		} else {
			cell = Formulas.getZaapiCell((short) mapId);
			if(cell < 0) {
				logger.warn("No zaapi cell for map {}", mapId);
				return;
			}
			// zaapi is free for aligned characters
		}

		character.setDisplacement(false);
		RolePlayMovement.teleport(client, map, cell);

		// Close the panel on client side
		session.write(isZaap ? "WV" : "Wv");

		logger.debug("{} used {} to map {} cell {}",
			new Object[]{ character.getName(), isZaap ? "zaap" : "zaapi", mapId, cell });
	}
}
