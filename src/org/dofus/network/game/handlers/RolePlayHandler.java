package org.dofus.network.game.handlers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.SpellsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dofus.network.game.Game;
import org.dofus.network.game.GameClient;
import org.dofus.network.game.GameClientHandler;
import org.dofus.network.game.handlers.parsers.AdminParser;
import org.dofus.network.game.handlers.parsers.BankParser;
import org.dofus.network.game.handlers.parsers.BasicParser;
import org.dofus.network.game.handlers.parsers.CraftParser;
import org.dofus.network.game.handlers.parsers.BoostParser;
import org.dofus.utils.ChatFilter;
import org.dofus.utils.DeferredSaveService;
import org.dofus.network.game.handlers.parsers.ChannelParser;
import org.dofus.network.game.handlers.parsers.DialogParser;
import org.dofus.network.game.handlers.parsers.ExchangeParser;
import org.dofus.network.game.handlers.parsers.FightParser;
import org.dofus.network.game.handlers.parsers.GameParser;
import org.dofus.network.game.handlers.parsers.GuildParser;
import org.dofus.network.game.handlers.parsers.InventoryParser;
import org.dofus.network.game.handlers.parsers.PartyParser;
import org.dofus.objects.items.Inventory;
import org.dofus.network.game.handlers.parsers.QuestParser;
import org.dofus.network.game.handlers.parsers.SpellParser;
import org.dofus.network.game.handlers.parsers.WaypointParser;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.utils.RegenService;
import org.dofus.utils.StringUtils;

public class RolePlayHandler extends GameClientHandler {

	private static final Logger logger = LoggerFactory.getLogger(RolePlayHandler.class);

	private final IoSession session;
	private final Characters character;

	protected RolePlayHandler(Game game, GameClient client) {
		super(game, client);
		this.session   = client.getSession();
		this.character = client.getCharacter();
		session.write("cC+" + character.getChannels());
		//Guild
		//Subarea
		
		session.write("al|"); //TODO Area align status
		//Spell
		SpellsData.loadCharacterSpells(character);
		session.write(SpellsData.buildSLPacket(character.getSpellBook())); //Spell list message
		session.write("AR" + character.getRestriction().toBase36());
		Inventory inv = character.getInventory();
		session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
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
		RegenService.refresh(character, session);
		FightParser.reconnectIfNeeded(character, session);
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
			case 'D':
				parseDialogPacket(packet);
			break;
			case 'E':
				ExchangeParser.parse(character, session, packet);
			break;
			case 'e':
				parseEnvironementPacket(packet);
			break;
			/*case 'F':
				parseFriendPacket(packet);
			break;*/
			case 'f':
				FightParser.parseFightPacket(character, session, packet);
			break;
			case 'G':
				parseGamePacket(packet);
			break;
			case 'g':
				GuildParser.parse(character, session, packet);
			break;
			case 'O': // Inventaire / équipement
				InventoryParser.parse(character, session, client, packet);
			break;
			case 'M': // Métier / artisanat
				CraftParser.parse(character, session, packet);
			break;
			case 'P':
				parsePartyPacket(packet);
			break;
			case 'Q':
				QuestParser.parse(character, session, packet);
			break;
			/*case 'R':
				parseMountPacket(packet);
			break;*/
			case 'S':
				SpellParser.parse(character, session, packet);
			break;
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

	private void parseDialogPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'C': // DC{actorId} — open dialog
				DialogParser.create(character, session, packet);
			break;
			case 'R': // DR{replyId} — chose reply
				DialogParser.reply(character, session, packet);
			break;
			case 'V': // DV — close dialog
				DialogParser.quit(character, session);
			break;
		}
	}

	private void parseBasicsPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'a': //Movement by click-map
				switch(packet.charAt(2)) {
					case 'M':
						BasicParser.moveByClickMap(character, session, client, packet);
					break;
				}
				break;
			case 'A': //Console admin client : BA!getitem 9131 1
				AdminParser.parseBasicAdmin(character, session, client, packet);
				break;
			case 'D': //Date
				break;
			case 'd': // Bd — ouverture banque
			case 'k': // Bk — dépôt kamas
			case 'q': // Bq — retrait kamas
			case 'i': // Bi — dépôt item
			case 'o': // Bo — retrait item
				BankParser.parse(character, session, packet);
				break;
			case 'M': //Message
				BasicParser.channelsMessage(character, session, client, packet);
				break;
			case 'S': //Emote
				BasicParser.emoticons(character, packet);
				break;
			case 'Y': //Character state
				BasicParser.states(packet);
				break;
		}
	}

	private void parseEnvironementPacket(String packet) {
		switch(packet.charAt(1)) {
			case 'D': // eD{orientation} — changement d'orientation (clic droit sur le personnage)
				if(packet.length() < 3) break;
				try {
					int orientOrd = Integer.parseInt(packet.substring(2));
					org.dofus.objects.actors.EOrientation orientation = org.dofus.objects.actors.EOrientation.valueOf(orientOrd);
					if(orientation == null) break;
					character.setCurrentOrientation(orientation);
					String broadcast = "eD" + character.getId() + "|" + orientOrd;
					for(Characters actor : character.getCurrentMap().getActors().values()) {
						IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
						if(actorSession != null && actorSession.isConnected())
							actorSession.write(broadcast);
					}
				} catch(NumberFormatException e) { /* ignore */ }
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
        case 'A': // GA — action (mouvement hors-combat OU action combat)
            if(FightParser.getFightForCharacter(character) != null) {
                // En combat → routage vers FightParser
                FightParser.parseAction(character, session, packet);
            } else {
                GameParser.action(session, client, packet);
            }
            break;
        case 'C': // GC — création personnage
            if(FightParser.getFightForCharacter(character) != null) {
                FightParser.reconnectIfNeeded(character, session);
            } else {
                GameParser.creation(character, session);
            }
            break;
        case 'I': // GI — informations carte
            if(FightParser.getFightForCharacter(character) != null) {
                FightParser.reconnectIfNeeded(character, session);
            } else {
                GameParser.information(character, session, client, character.getCurrentMap());
            }
            break;
        case 'K': // GK — fin d'action
        	if(packet.length() > 2)
        	    GameParser.endAction(client, packet.charAt(2) == 'K', packet.substring(3));
            break;
        case 'p': // Gp{cell} - choix cellule placement
            if(FightParser.getFightForCharacter(character) != null && packet.length() > 2) {
                FightParser.choosePlacementCell(character, session, packet.substring(2));
            }
            break;
        case 'R': // GR{groupId} — attaque un groupe de monstres
            if(packet.length() > 2) {
                if(FightParser.getFightForCharacter(character) != null
                        && packet.length() == 3
                        && (packet.charAt(2) == '0' || packet.charAt(2) == '1')) {
                    FightParser.setReady(character, session, packet.charAt(2) == '1');
                    break;
                }
                try {
                    int groupId = Integer.parseInt(packet.substring(2));
                    GameParser.attackMonsterGroup(session, client, groupId);
                } catch(NumberFormatException e) { /* ignore */ }
            }
            break;
        case 'S': // GS — passer son tour en combat (alias de fN)
            if(FightParser.getFightForCharacter(character) != null) {
                FightParser.parseFightPacket(character, session, "fN");
            }
            break;
        case 't': // Gt - fin de tour officielle
            if(FightParser.getFightForCharacter(character) != null) {
                FightParser.passTurn(character, session);
            }
            break;
        }
	}

	@Override
	public void onClosed() {
        org.dofus.game.fight.Fight activeFight = FightParser.getFightForCharacter(character);
        if(activeFight != null && activeFight.getState() != org.dofus.game.fight.Fight.State.FINISHED) {
            FightParser.markDisconnected(character);
            DeferredSaveService.cancel(character.getId());
            CharactersData.update(character);
            WorldData.removeSessionByAccount(client.getAccount());
            WorldData.removeController(character.getId());
            character.setConnected(false);
            client.getAccount().setConnected(false);
            ChatFilter.remove(character.getId());
            BankParser.evictCache(client.getAccount().getId());
            RegenService.stop(character);
            logger.debug("RolePlayHandler closed for character {} during fight {}", character.getName(), activeFight.getId());
            return;
        }

		//Check si c'est une déconnexion ou un changement de perso
		character.getCurrentMap().removeActor(character);
		List<Characters> snapshot = new ArrayList<>(character.getCurrentMap().getActors().values());
        for(Characters actor : snapshot) {
        	IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
        	if(actorSession != null && actorSession.isConnected() && !actorSession.equals(client.getSession()))
        		actorSession.write("GM|-" + character.getId());
       	}
        
        DeferredSaveService.cancel(character.getId());
        CharactersData.update(character);

		WorldData.removeCharacterById(character.getId());
		WorldData.removeCharacterByName(character.getName());
		WorldData.removeSessionByAccount(client.getAccount());
		WorldData.removeController(character.getId());
		CharactersData.removeCharacter(character);
		
		character.setDialogNpc(null);
		character.setConnected(false);
		client.getAccount().setConnected(false);

		// Nettoyage des services stateful
		ChatFilter.remove(character.getId());
		BankParser.evictCache(client.getAccount().getId());
		RegenService.stop(character);

		logger.debug("RolePlayHandler closed for character {}", character.getName());
	}
}
