package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.MapsData;
import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.utils.Formulas;

public class WaypointParser {

	public static void use(Characters character, IoSession session, GameClient client, String packet) {
		short id = Short.parseShort(packet.substring(2));
		if(packet.charAt(1) == 'U') {
			int cost = 999;
			if(!character.isDisplacement() || character.getKamas() < cost)
				return;
			
			MapTemplate map = MapsData.findById(id);
			short cellId = Formulas.getZaapCell((short) map.getId());
			
			RolePlayMovement.teleport(client, map, cellId);
			character.setKamas(-cost);
			session.write(Statistic.getStatisticsMessage(character));
			session.write("WV");
			character.setDisplacement(false);
		} else {
			int cost = 999;
			if(!character.isDisplacement() || character.getKamas() < cost)
				return;
			
			MapTemplate map = MapsData.findById(id);
			short cellId = Formulas.getZaapiCell((short) map.getId());
			
			RolePlayMovement.teleport(client, map, cellId);
			character.setKamas(-cost);
			session.write(Statistic.getStatisticsMessage(character));
			session.write("Wv");
			character.setDisplacement(false);
		}
	}

	public static void panelZaapis(Characters character, IoSession session) {
		if(!character.isDisplacement())
			return;
		character.setDisplacement(false);
		session.write("Wv");
	}

	public static void panelZaaps(Characters character, IoSession session) {
		if(!character.isDisplacement())
			return;
		character.setDisplacement(false);
		session.write("WV");
	}
}
