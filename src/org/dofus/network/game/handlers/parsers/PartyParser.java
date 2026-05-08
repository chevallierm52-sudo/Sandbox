package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Party;

public class PartyParser {

	public static void accept(Characters target, IoSession targetSession) {
		Characters character = WorldData.getCharacterByName().get(target.getInvitation());
		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
		Party party = character.getParty();
		
		if(party == null) {
			party = new Party(character, target);
			
			targetSession.write("PCK" + party.getChief().getName());
			targetSession.write("PL" + party.getChief().getId());
			
			session.write("PCK" + party.getChief().getName());
			session.write("PL" + party.getChief().getId());
						
			target.setParty(party);
			character.setParty(party);
			
			StringBuilder sb = new StringBuilder().append("PM+");
			
			boolean first = true;
			for(Characters player : party.getMembers().values()) {
				if(!first)
					sb.append("|");
				sb.append(player.parseParty());
				first = false;
			}
			
			targetSession.write(sb.toString());
			session.write(sb.toString());
			
		} else {
			targetSession.write("PCK" + party.getChief().getName());
			targetSession.write("PL" + party.getChief().getId());
			
			StringBuilder base = new StringBuilder().append("PM+");
			for(Characters player : party.getMembers().values()) {
				IoSession players = WorldData.getSessionByAccount().get(player.getOwner());
				players.write(base.append(target.parseParty()).toString());
			}
			
			StringBuilder allMembers = new StringBuilder().append("PM+");
			boolean isFirst = true;
			for(Characters player : party.getMembers().values()) {
				if(!isFirst)
					allMembers.append("|");
				allMembers.append(player.parseParty());
				isFirst = false;
			}
			
			target.setParty(party);
			party.addMember(target);
			
			targetSession.write(allMembers.append("|").append(target.parseParty()).toString());
		}
		session.write("PR");
	}

	public static void invitation(Characters character, IoSession session, String name) {
		Characters target = WorldData.getCharacterByName().get(name);
		
		if(!target.isConnected()) {
			session.write("PIEn" + name);
			return;
		} else if(target.getParty() != null) {
			session.write("PIEa" + name);
			return;
		} else if(character.getParty() != null && character.getParty().getMembersSize() == 8) {
			session.write("PIEf");
		}
		
		target.setInvitation(character.getName());
		character.setInvitation(target.getName());
		
		IoSession targetSession = WorldData.getSessionByAccount().get(target.getOwner());
    	
		targetSession.write("PIK" + character.getName() + "|" + target.getName());
		session.write("PIK" + character.getName() + "|" + target.getName());

	}

	public static void refuse(Characters character, IoSession session) {
		session.write("BN");
		Characters target = WorldData.getCharacterByName().get(character.getInvitation());
		character.setInvitation(null);
		target.setInvitation(null);
		IoSession targetSession = WorldData.getSessionByAccount().get(target.getOwner());
    	targetSession.write("PR");
	}

	public static void leave(Characters character, IoSession session, String packet) {
		Party party = character.getParty();
		if(packet.equals("PV")) { //Leave alone
			party.leave(character);
		} else { //Kick
			int characterId = Integer.parseInt(packet.substring(2));
			Characters kick = WorldData.getCharacters().get(characterId);
			
			party.leave(kick);
		}
	}

}
