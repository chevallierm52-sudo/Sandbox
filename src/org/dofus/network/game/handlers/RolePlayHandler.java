package org.dofus.network.game.handlers;

import java.util.Date;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.CharactersData;
import org.dofus.network.game.Game;
import org.dofus.network.game.GameClient;
import org.dofus.network.game.GameClientHandler;
import org.dofus.network.game.handlers.parsers.BasicParser;
import org.dofus.network.game.handlers.parsers.BoostParser;
import org.dofus.network.game.handlers.parsers.ChannelParser;
import org.dofus.network.game.handlers.parsers.GameParser;
import org.dofus.network.game.handlers.parsers.PartyParser;
import org.dofus.network.game.handlers.parsers.WaypointParser;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.utils.StringUtils;

public class RolePlayHandler extends GameClientHandler {
	
	IoSession session = client.getSession();
	Characters character = client.getCharacter();
	
	protected RolePlayHandler(Game game, GameClient client) {
		super(game, client);
		session.write("cC+" + character.getChannels());
		//Guild
		//Subarea
		
		session.write("al|"); //TODO Area align status
		//Spell
		session.write("SL"); //Spell list message
		session.write("AR" + character.getRestriction().toBase36());
		session.write("Ow0|" + character.getMaxPods()); //TODO "Ow" + usedPods + "|" + maxPods;
		session.write("eL0|"); //Quest?
		session.write("BD" + StringUtils.CURRENT_DATE_FORMATTER.format(new Date())); // Send actual time
		
		session.write("ZS" + character.getAlignmentType()); //Send alignment id
		session.write("Im153;127.0.0.1"); //FIXME Address
		session.write("Im189"); //Hello message
		//LastCo and last address here TODO
		
		/*
			Characters character = character;
			if(character.getAlignment() > 0 && character.getAlignment() != 3 
				&& character.getCurrentMap().getAlignment() > 0 
				&& character.getCurrentMap().getAlignement() != character.getAlignment()) {
				
				session.write("Im091");
				//save pos
			}
			*/
		if(character.getEnergy() <= 2000)
			session.write("M111|" + character.getEnergy());
		session.write("IC|"); //On remet à zéro les drapeaux
		session.write("BT82800000");//FIXME idk Gestion de la nuit
		//Regen life
	}
	
	@Override
	public void parse(String packet) throws Exception {
        switch(packet.charAt(0)) {
	        case 'A':
				switch(packet.charAt(1)) {
				case 'B':
					BoostParser.boost(character, session, Integer.parseInt(packet.substring(2)));
				break;
				}
			break;
			case 'B':
				parseBasicsPacket(packet);
			break;/*
			case 'C':
				parseConquestPacket(packet);
			break;*/
			case 'c':
				parseChannelPacket(packet);
			break;
			/*case 'D':
				parseDialogPacket(packet);
			break;		
			case 'E':
				parseExchangePacket(packet);
			break;
			case 'e':
				parseEnvironementPacket(packet);
			break;
			case 'F':
				parseFriendPacket(packet);
			break;
			case 'f':
				parseFightPacket(packet);
			break;*/
			case 'G':
				parseGamePacket(packet);
			break;
			/*case 'g':
				parseGuildPacket(packet);
			break;
			case 'O':
				parseObjectPacket(packet);
			break;*/
			case 'P':
				parsePartyPacket(packet);
			break;/*
			case 'Q':
				parseQuestPacket(packet);
			break;
			case 'R':
				parseMountPacket(packet);
			break;
			case 'S':
				parseSpellPacket(packet);
			break;*/
			case 'W':
				parseWaypointPacket(packet);
			break;
        }
	}

	private void parsePartyPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'A':
				PartyParser.accept(character, session);
			break;
			case 'I':
				PartyParser.invitation(character, session, packet.substring(2));
			break;
			case 'R':
				PartyParser.refuse(character, session);
			break;
			case 'V':
				PartyParser.leave(character, session, packet);
			break;
		}
	}

	private void parseWaypointPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'p':
				//create prism
			break;
			case 'u':
			case 'U'://Use
				WaypointParser.use(character, session, client, packet);
			break;
			case 'v'://Zaapis
				WaypointParser.panelZaapis(character, session);
			break;
			case 'V'://Zaap
				WaypointParser.panelZaaps(character, session);
			break;
			case 'w':
				//Prism quit
			break;
		}
	}

	private void parseBasicsPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'a': //Movement by click-map
				switch(packet.charAt(2)) {
					case 'M':
						BasicParser.moveByClickMap(packet);
					break;
				}
				break;
			case 'A': //Console
				break;
			case 'D': //Date
				break;
			case 'M': //Message
				BasicParser.channelsMessage(character, session, packet);
				break;
			case 'S': //Emote
				BasicParser.emoticons(character, packet);
				break;
			case 'Y': //Character state
				BasicParser.states(packet);
				break;
		}
	}

	private void parseChannelPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'C': //Change channel
				ChannelParser.change(character, packet);
			break;
		}
	}

	private void parseGamePacket(String packet) throws Exception {
        switch(packet.charAt(1)) {
        case 'A': //Action
        	GameParser.action(session, client, packet);
            break;
        case 'C': //GameCreation
        	GameParser.creation(character, session);
            break;

        case 'I': //GameInformation
        	GameParser.information(character, session, client, character.getCurrentMap());
            break;

        case 'K': //End Action
        	GameParser.endAction(client, packet.charAt(2) == 'K', packet.substring(3));
            break;
        }
	}

	@Override
	public void onClosed() {
		//Check si c'est une déconnexion ou un changement de perso
		character.getCurrentMap().removeActor(character);
        for(Characters actor : character.getCurrentMap().getActors().values()) {
        	IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
        	if(!actorSession.equals(client.getSession()))
        		actorSession.write("GM|-" + character.getId());
       	}
        
        CharactersData.update(character);
        
		WorldData.removeCharacterById(character.getId());
		WorldData.removeCharacterByName(character.getName());
		WorldData.removeSessionByAccount(client.getAccount());
		CharactersData.removeCharacter(character);
		
		character.setConnected(false);
		client.getAccount().setConnected(false);
		System.out.println("RolePlayHandler : onClosed()");
	}
}
