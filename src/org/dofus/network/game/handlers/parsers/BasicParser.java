package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;

public class BasicParser {
	
	@SuppressWarnings("unused")
	public static void moveByClickMap(String packet) {
		//FIXME: Restriction game master
		if(!packet.substring(3).equalsIgnoreCase("NaN")) {
			String[] data = packet.substring(3).split(",");
			//FIXME: Load map by X, Y coordinate
			int abscissa = Integer.parseInt(data[0]);
			int ordinate = Integer.parseInt(data[1]);
			//if(!map == null) teleport
		}
	}

	public static void channelsMessage(Characters character, IoSession session, String packet) {
		/**
		 * TODO: Player muted send BN, correct message etc...
		 * Level 10 if i remember to speak on sell/recruit
		 * XXX: Special packet for fight
		 */
		packet.replace("<", ""); packet.replace(">", "");
		if(packet.length() == 3) return;
		
		StringBuilder message = new StringBuilder().append(packet.split("\\|", 2) [1]);
		
		switch(packet.charAt(2)) {
			case '*': //Default
				for(Characters actor : character.getCurrentMap().getActors().values()) {
		        	IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
		        	if(actorSession != null)
		        		actorSession.write("cMK|" + character.getId() + "|" + character.getName() + "|" + message.toString());
		       	}
				break;
			case '#': //TODO Fight channel
				break;
			case ':': //Sell channel TODO make better no-flood
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
			case '?': //Recruit channel
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
