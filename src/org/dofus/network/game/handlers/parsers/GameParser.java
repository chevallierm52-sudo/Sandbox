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
import org.dofus.objects.items.GroundItem;
import org.dofus.utils.GroundItemService;
import org.dofus.utils.InteractiveObjectService;
import org.apache.mina.core.session.IoSession;
import org.dofus.game.actions.IGameAction.ActionTypeEnum;
import org.dofus.game.actions.IGameAction.GameActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameParser {

	private static final Logger logger = LoggerFactory.getLogger(GameParser.class);

	public static void action(IoSession session, GameClient client, String packet) {
		if(packet.length() < 5) {
			sendActionCancel(session);
			return;
		}
        ActionTypeEnum actionType = ActionTypeEnum.valueOf(Integer.parseInt(packet.substring(2, 5)));
        if(actionType == null) {
            logger.warn("Unknown actionType for parseGamePacket: {}", packet);
            return;
        }
        switch(actionType){
        case MOVEMENT:
            if(client.isBusy()) {
                if(!client.getActions().isEmpty()
                        && client.getActions().peek().getActionType() == GameActionType.MOVEMENT) {
                    // Sécurité anti-blocage : un nouveau GA001 alors qu'une ancienne marche est
                    // encore en pile signifie presque toujours que le serveur a gardé une action
                    // morte (ex: BaM/changement de carte avant GKK). On la purge et on traite
                    // le nouveau déplacement.
                    client.getActions().clear();
                } else {
                    sendActionCancel(session);
                    break;
                }
            }

            RolePlayMovement movement = new RolePlayMovement(packet.substring(5), client);
            if(!movement.isValid()) {
                sendActionCancel(session);
                return;
            }
            client.getActions().push(movement);

            movement.begin();
            break;
        case MAP_ACTION:
            handleMapAction(session, client, packet.substring(5));
            break;
        case FIGHT_AGGRESSION:
            handleFightAggression(session, client, packet.substring(5));
            break;
		default:
			logger.warn("Unknown actionType for parseGamePacket: {}", packet);
			break;
		}
	}

    private static void handleMapAction(IoSession session, GameClient client, String args) {
        if(client == null || client.getCharacter() == null || args == null || args.isEmpty()) {
            sendActionCancel(session);
            return;
        }

        String[] parts = args.split(";");
        try {
            short cellId = Short.parseShort(parts[0]);
            int skillId = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;

            if(client.isBusy() && !client.getActions().isEmpty()
                    && client.getActions().peek().getActionType() == GameActionType.MOVEMENT) {
                ((RolePlayMovement) client.getActions().peek()).queueMapAction(cellId, skillId);
                return;
            }
            if(client.isBusy()) {
                session.write("BN");
                return;
            }

            if(GroundItemService.pickup(client.getCharacter(), session, cellId)) {
                return;
            }

            if(!InteractiveObjectService.use(client.getCharacter(), session, cellId, skillId)) {
                logger.debug("GA500 ignore : aucune action sur cell={} args={}", cellId, args);
            }
        } catch(NumberFormatException e) {
            session.write("BN");
        }
    }

    private static void handleFightAggression(IoSession session, GameClient client, String args) {
        if(args == null || args.isEmpty()) {
            session.write("BN");
            return;
        }
        try {
            attackMonsterGroup(session, client, Integer.parseInt(args.split(";")[0]));
        } catch(NumberFormatException e) {
            session.write("BN");
        }
    }

    public static void attackMonsterGroup(IoSession session, GameClient client, int groupId) {
        if(client == null || client.getCharacter() == null) {
            if(session != null) session.write("BN");
            return;
        }
        if(client.isBusy()) {
            if(!client.getActions().isEmpty()
                    && client.getActions().peek().getActionType() == GameActionType.MOVEMENT) {
                ((RolePlayMovement) client.getActions().peek()).queueFight(groupId);
            } else if(session != null) {
                session.write("BN");
            }
            return;
        }
        FightParser.initiateFightVsMonsters(client.getCharacter(), session, groupId);
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
        if(!map.isValidActorCell(character.getCurrentCell())) {
            Short safeCell = map.findNearestValidActorCell(character.getCurrentCell(), true);
            if(safeCell != null) character.setCurrentCell(safeCell.shortValue());
        }
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

        // Objets deja deposes au sol sur cette carte.
        for(GroundItem groundItem : GroundItemService.getForMap(map.getId())) {
            session.write(groundItem.toGDOPacket());
        }
	 
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

            short cellId = parseCancelCell(args, client.getCharacter().getCurrentCell());
            ((RolePlayMovement) client.getActions().pop()).cancel(cellId);
        }
	}

    private static short parseCancelCell(String args, short fallback) {
        if(args == null || args.isEmpty()) return fallback;
        int separator = args.indexOf('|');
        String cell = separator >= 0 ? args.substring(separator + 1) : args;
        if(cell.isEmpty()) return fallback;
        try {
            return Short.parseShort(cell);
        } catch(NumberFormatException e) {
            return fallback;
        }
    }

    private static void sendActionCancel(IoSession session) {
        if(session != null) session.write("GA;0");
    }

}
