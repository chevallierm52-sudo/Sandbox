package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartyParser {

	private static final Logger logger = LoggerFactory.getLogger(PartyParser.class);

	public static void accept(Characters target, IoSession targetSession) {
		String inviterName = target.getInvitation();
		if(inviterName == null) return;

		Characters character = WorldData.getCharacterByName().get(inviterName);
		if(character == null) return;

		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
		if(session == null || !session.isConnected()) return;

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
				if(!first) sb.append("|");
				sb.append(player.parseParty());
				first = false;
			}

			targetSession.write(sb.toString());
			session.write(sb.toString());

		} else {
			targetSession.write("PCK" + party.getChief().getName());
			targetSession.write("PL" + party.getChief().getId());

			for(Characters player : party.getMembers().values()) {
				IoSession memberSession = WorldData.getSessionByAccount().get(player.getOwner());
				if(memberSession == null || !memberSession.isConnected()) continue;
				memberSession.write("PM+" + target.parseParty());
			}

			StringBuilder allMembers = new StringBuilder().append("PM+");
			boolean isFirst = true;
			for(Characters player : party.getMembers().values()) {
				if(!isFirst) allMembers.append("|");
				allMembers.append(player.parseParty());
				isFirst = false;
			}

			target.setParty(party);
			party.addMember(target);

			targetSession.write(allMembers.append("|").append(target.parseParty()).toString());
		}

		target.setInvitation(null);
		character.setInvitation(null);
		session.write("PR");
	}

	public static void invitation(Characters character, IoSession session, String name) {
		Characters target = WorldData.getCharacterByName().get(name);

		if(target == null || !target.isConnected()) {
			session.write("PIEn" + name);
			return;
		} else if(target.getParty() != null) {
			session.write("PIEa" + name);
			return;
		} else if(character.getParty() != null && character.getParty().getMembersSize() >= 8) {
			session.write("PIEf");
			return;
		}

		IoSession targetSession = WorldData.getSessionByAccount().get(target.getOwner());
		if(targetSession == null || !targetSession.isConnected()) {
			session.write("PIEn" + name);
			return;
		}

		target.setInvitation(character.getName());
		character.setInvitation(target.getName());

		targetSession.write("PIK" + character.getName() + "|" + target.getName());
		session.write("PIK" + character.getName() + "|" + target.getName());
	}

	public static void refuse(Characters character, IoSession session) {
		session.write("BN");

		String inviterName = character.getInvitation();
		if(inviterName == null) return;

		Characters target = WorldData.getCharacterByName().get(inviterName);
		character.setInvitation(null);
		if(target == null) return;

		target.setInvitation(null);
		IoSession targetSession = WorldData.getSessionByAccount().get(target.getOwner());
		if(targetSession != null && targetSession.isConnected())
			targetSession.write("PR");
	}

	public static void leave(Characters character, IoSession session, String packet) {
		Party party = character.getParty();
		if(party == null) return;

		if(packet.equals("PV")) {
			party.leave(character);
		} else {
			try {
				int characterId = Integer.parseInt(packet.substring(2));
				Characters kick = WorldData.getCharacters().get(characterId);
				if(kick != null)
					party.leave(kick);
			} catch(NumberFormatException e) {
				logger.warn("Invalid kick packet: {}", packet);
			}
		}
	}
}
