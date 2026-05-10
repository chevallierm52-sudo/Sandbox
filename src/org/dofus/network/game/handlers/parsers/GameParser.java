package org.dofus.network.game.handlers.parsers;

import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.network.game.protocols.GProtocol;
import org.dofus.objects.WorldData;
import org.dofus.database.objects.MonstersData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.NPC;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.maps.MapTemplate;
import org.apache.mina.core.session.IoSession;
import org.dofus.game.actions.IGameAction.ActionTypeEnum;
import org.dofus.game.actions.IGameAction.GameActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameParser {

	private static final Logger logger = LoggerFactory.getLogger(GameParser.class);

	public static void action(IoSession session, GameClient client, String packet) {
		if(packet.length() < 5) {
			session.write("BN");
			return;
		}
    	switch(ActionTypeEnum.valueOf(Integer.parseInt(packet.substring(2, 5)))){
        case MOVEMENT:
            if(client.isBusy())
                session.write("BN");
            else {
                RolePlayMovement movement = new RolePlayMovement(packet.substring(5), client);
                client.getActions().push(movement);

                movement.begin();
            }
            break;
		default:
			logger.warn("Unknown actionType for parseGamePacket: {}", packet);
			break;
		}
	}

	public static void creation(Characters character, IoSession session) {
		session.write("GCK|1|");
		session.write(Statistic.getStatisticsMessage(character));
    	session.write("GDM|"
    			+ character.getCurrentMap().getId() + "|"
    			+ character.getCurrentMap().getDate() + "|"
    			+ character.getCurrentMap().getKey() + "|");
    	// fC0 envoyé ici (phase GC) ET dans information() après GDK (phase GI),
    	// comme AncestraRemake et Shivas le font (deux envois, comportement attendu).
    	session.write("fC0");
	}

	//FIXME BOT MOUVEMENT : c'es
	public static void information(Characters character, IoSession session, GameClient client, MapTemplate map) {
		MonstersData.spawnAll(map); //FIXME On spawn le groupe de monstre seulement map/map ça va pas... FAUT Chargé toute les maps concernant un groupe de monstre dessus, le reste des map on le garde en lazy-loading
		if(map.getActor(character.getId()) == null)
	    	map.addActor(character);

	    // Ordre conforme AncestraRemake/Shivas : personnages d'abord, PNJ ensuite.
	    // Les personnages (y compris le joueur courant, déjà ajouté à la map ci-dessus)
	    // sont envoyés en premier, puis les PNJ dans le même paquet GM.
	    StringBuilder allActors = new StringBuilder("GM");

	    // 1) Personnages (joueurs + bots)
	    for (Characters actor : map.getActors().values()) {
	        allActors.append("|+");
	        GProtocol.getCharacterPattern(allActors, actor);
	    }

	    // 2) PNJ (champ 6 = -4, discriminant NPC pour le client Flash)
	    for(NPC npc : map.getNpcs().values()) {
	        allActors.append("|+");
	        GProtocol.getNpcPattern(allActors, npc);
	    }
	    
	    // 3) Groupes de monstres
	    for (MonsterGroup group : map.getMonsterGroups().values()) {
	        allActors.append("|+");
	        allActors.append(group.toGMEntry());
	    }

	    session.write(allActors.toString());
	 
	    StringBuilder newActor = new StringBuilder("GM|+");
	    GProtocol.getCharacterPattern(newActor, character);
	 
	    for (Characters actor : map.getActors().values()) {
	        if (actor == character) {
	            continue;
	        }
	 
	        Object controller = WorldData.getController(actor.getId());
	 
	        if (!(controller instanceof GameClient)) {
	            continue;
	        }
	 
	        GameClient gc = (GameClient) controller;
	 
	        if (gc.getSession() != null && gc.getSession().isConnected()) {
	            gc.getSession().write(newActor.toString());
	        }
	    }
	 
	    session.write("GDK");
	    session.write("fC" + 0);
	}

	public static void endAction(GameClient client, boolean success, String args) throws Exception {
		if(client.getActions().isEmpty())
			return;

        if(success) {
            client.getActions().pop().end();
        } else {
            if(client.getActions().peek().getActionType() != GameActionType.MOVEMENT)
                throw new Exception("invalid action : peeked action isn't a movement");

            short cellId = (args.length() >= 3) ? Short.parseShort(args.substring(2)) : client.getCharacter().getCurrentCell();
            ((RolePlayMovement) client.getActions().pop()).cancel(cellId);
        }
	}

}
