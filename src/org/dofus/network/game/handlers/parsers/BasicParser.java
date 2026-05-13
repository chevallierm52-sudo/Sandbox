package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.MapsData;
import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.BotBehavior;
import org.dofus.objects.actors.BotLearning;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.utils.ChatFilter;
import org.dofus.utils.Formulas;
import org.dofus.utils.PacketValidator;
import org.dofus.utils.ServerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicParser {

	private static final Logger logger = LoggerFactory.getLogger(BasicParser.class);

	public static void moveByClickMap(Characters character, IoSession session, GameClient client, String packet) {
		if(!character.getRight().canMoveAllDirections()) return;

		String data = packet.substring(3);
		if(data.equalsIgnoreCase("NaN")) return;

		String[] parts = data.split(",");
		if(parts.length < 2) return;

		try {
			int x = Integer.parseInt(parts[0]);
			int y = Integer.parseInt(parts[1]);

			MapTemplate map = MapsData.findByCoord(x, y);
			if(map == null) return;

			short cell = Formulas.getZaapCell((short) map.getId());
			if(cell < 0) cell = 200;
			if(!map.isValidActorCell(cell, true)) {
				Short safe = map.findNearestValidActorCell(cell, true);
				if(safe != null) cell = safe.shortValue();
			}

			// Un clic mini-carte peut arriver pendant un déplacement non terminé.
			// La téléportation nettoie la pile d'actions pour éviter le blocage BUSY.
			RolePlayMovement.teleport(client, map, cell);
		} catch(NumberFormatException e) {
			// malformed packet, ignore
		}
	}

	public static void channelsMessage(Characters character, IoSession session, GameClient client, String packet) {
		packet = packet.replace("<", "").replace(">", "");
		if(packet.length() == 3) return;

		StringBuilder message = new StringBuilder().append(packet.split("\\|", 2) [1]);

		// Validation anti-flood chat
		if(!PacketValidator.validateChatMessage(session.getId(), message.toString())) {
			session.write("BN");
			return;
		}
		// Anti-spam : doublon, flood rapide, caps, répétition
		if(!ChatFilter.allow(character.getId(), message.toString())) {
			session.write("BN");
			return;
		}

		// ── Commandes admin (préfixe '.') ─────────────────────────────────────
		if(packet.charAt(2) == '*' && message.length() > 0 && message.charAt(0) == '.') {
			AdminParser.parse(character, session, client, message.substring(1));
			return;
		}
		
		switch(packet.charAt(2)) {
			case '*': //Default
				for(Characters actor : character.getCurrentMap().getActors().values()) {
		        	IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
		        	if(actorSession != null)
		        		actorSession.write("cMK|" + character.getId() + "|" + character.getName() + "|" + message.toString());
		       	}
				// Renforcement apprentissage : le joueur a parlé sur cette map
				BotLearning.onPlayerSpoke(character.getCurrentMap().getId());
				// Réaction des bots présents sur la map
				{
					String senderName = character.getName();
					String msg        = message.toString();
					for(Characters actor : new java.util.ArrayList<>(character.getCurrentMap().getActors().values())) {
						if(actor.getId() < 0) // IDs négatifs = bots
							BotBehavior.onMapMessage(actor, senderName, msg);
					}
				}
				break;
			case '#': //TODO Fight channel
				break;
			case ':': //Sell channel — level 10 requis
				if(character.getExperience().getLevel() < 10) {
					session.write("BN");
					break;
				}
				for(Characters actor : WorldData.getCharacters().values()) {
					IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
					if(actorSession != null)
		        		actorSession.write("cMK:|" + character.getId() + "|" + character.getName() + "|" + message.toString());
				}
				break;
			case '@': //Administration TODO game master
				for(Characters actor : WorldData.getCharacters().values()) {
					IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
					if(actorSession != null)
		        		actorSession.write("cMK@|" + character.getId() + "|" + character.getName() + "|" + message.toString());
				}
				break;
			case '?': //Recruit channel — level 10 requis
				if(character.getExperience().getLevel() < 10) {
					session.write("BN");
					break;
				}
				for(Characters actor : WorldData.getCharacters().values()) {
					IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
					if(actorSession != null)
		        		actorSession.write("cMK?|" + character.getId() + "|" + character.getName() + "|" + message.toString());
				}
				break;
			case '!': //Alignement channel TODO
				break;
			// case 'i' pour channel information mais il est en moins cC-i TODO
			default:
				String name = packet.substring(2).split("\\|") [0];

				Characters target = WorldData.getCharacterByName().get(name);
				if(target == null || !target.isConnected()) {
					session.write("cMEf" + name);
					break;
				}

				IoSession sessionTarget = WorldData.getSessionByAccount().get(target.getOwner());
				if(sessionTarget == null) {
					session.write("cMEf" + name);
					break;
				}

				//Im114;target.getName() if away / invi
				session.write("cMKT|" + target.getId() + "|" + target.getName() + "|" + message.toString());
				sessionTarget.write("cMKF|" + character.getId() + "|" + character.getName() + "|" + message.toString());
		}
	}

	public static void emoticons(Characters character, String packet) {
		/**
		 * XXX If in fight we have special packet to send
		 */
		int id = Integer.parseInt(packet.substring(2));
        for(Characters actor : character.getCurrentMap().getActors().values()) {
        	IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
        	if(actorSession != null)
        		actorSession.write("cS" + character.getId() + "|" + id);
       	}
	}

	public static void states(String packet) {
		switch(packet.charAt(2)) {
		case 'A': //Away
			/** FIXME No-spam , Set chr away
			 * if away Im038(away = false) else Im037(away = true)
			 */
			break;
		case 'I': //Invisible
			/** FIXME Set chr invisible
			 * if invi Im051(invi = false) else Im050(invi = true)
			 */
			break;
	}
	}

}
