package org.dofus.game.actions;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.MapsData;
import org.dofus.network.game.GameClient;
import org.dofus.network.game.handlers.parsers.FightParser;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.utils.GroundItemService;
import org.dofus.utils.InteractiveObjectService;

public class RolePlayMovement implements IGameAction {

	private final GameClient client;
    private final String path;
    private String effectivePath = "";
    private short destinationCell = -1;
    private short blockedTargetCell = -1;
    private short pendingActionCell = -1;
    private int pendingActionSkill = -1;
    private int pendingFightGroupId = -1;
    private boolean prepared = false;
    private boolean valid = false;
    
	public RolePlayMovement(String path, GameClient client) {
		this.path = path;
		this.client = client;
	}
	
	public static void teleport(GameClient client, MapTemplate nextMap, short cellId) {
        if(client == null || client.getCharacter() == null || nextMap == null)
        	return;

        // Un changement de carte annule forcément l'action de déplacement courante.
        // Sinon le client peut rester bloqué en état BUSY si un BaM intervient avant le GKK.
        client.getActions().clear();

        String removePacket = "GM|-" + client.getCharacter().getId();
        for(Characters actor : client.getCharacter().getCurrentMap().getActors().values()) {
            if(actor == client.getCharacter()) continue;
            IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
            if(actorSession == null || !actorSession.isConnected()) continue;
            actorSession.write(removePacket);
        }
        
        client.getCharacter().getCurrentMap().removeActor(client.getCharacter());
        client.getCharacter().setCurrentMap(nextMap);
        client.getCharacter().setCurrentCell(cellId);
        
        client.getCharacter().getCurrentMap().addActor(client.getCharacter());

        client.getSession().write("GA;2;" + client.getCharacter().getId() + ";");
        
        client.getSession().write("GDM|" //Send map data
    			+ client.getCharacter().getCurrentMap().getId() + "|"
    			+ client.getCharacter().getCurrentMap().getDate() + "|"
    			+ client.getCharacter().getCurrentMap().getKey() + "|"
    			);
        
    }

	@Override
	public GameActionType getActionType() {
		return GameActionType.MOVEMENT;
	}

	@Override
	public void begin() {
		if(!isValid()) {
            sendMovementCancel();
			return;
		}
        if(!hasMovement()) {
            short cellId = client.getCharacter().getCurrentCell();
            broadcastAction("GA1;4;" + client.getCharacter().getId() + ";"
                    + client.getCharacter().getId() + "," + cellId);
            return;
        }

		broadcastAction("GA1;1;" + client.getCharacter().getId() + ";" + this.getPath());
	}

	@Override
	public void end() {
        preparePath();
		if(!valid)
			return;

        boolean moved = hasMovement();
        short cellId = destinationCell;
        if(moved) {
            EOrientation orientation = Cell.decode(effectivePath.charAt(effectivePath.length() - 3));

            if(!isValidDestination(cellId)) {
                sendMovementCancel();
                return;
            }

            client.getCharacter().setCurrentOrientation(orientation);
            client.getCharacter().setCurrentCell(cellId);

            tryPickupGroundItem(cellId);
        }
        if(runPendingMapAction() || runPendingFight())
            return;
        if(!moved)
            return;

        TriggerTemplate trigger = client.getCharacter().getCurrentMap().getTriggers().get(cellId);

        if(trigger != null)
            teleport(client, MapsData.load(trigger.getNextMap()), trigger.getNextCellId());
	}

	@Override
	public void cancel() {
        pendingActionCell = -1;
        pendingActionSkill = -1;
        pendingFightGroupId = -1;
	}

    public void cancel(short cellId) {
    	client.getCharacter().setCurrentCell(cellId);
        cancel();
    }

    private void tryPickupGroundItem(short cellId) {
        GroundItemService.pickup(client.getCharacter(), client.getSession(), cellId);
    }
    
    public String getPath() {
        preparePath();
        return "a" + Cell.encode(client.getCharacter().getCurrentCell()) + effectivePath;
    }

    public boolean isValid() {
        preparePath();
        return valid;
    }

    public boolean hasMovement() {
        preparePath();
        return effectivePath != null && effectivePath.length() >= 3;
    }

    public void queueMapAction(short cellId, int skillId) {
        pendingActionCell = cellId;
        pendingActionSkill = skillId;
    }

    public void queueFight(int groupId) {
        pendingFightGroupId = groupId;
    }

    private void broadcastAction(String packet) {
        for(Characters actors : client.getCharacter().getCurrentMap().getActors().values()) {
            IoSession actorSession = WorldData.getSessionByAccount().get(actors.getOwner());
            if(actorSession == null || !actorSession.isConnected())
                continue;
            actorSession.write(packet);
        }
    }

    private void preparePath() {
        if(prepared) return;
        prepared = true;
        valid = false;
        effectivePath = "";

        if(path == null || path.length() < 3 || (path.length() % 3) != 0
                || client == null || client.getCharacter() == null)
            return;

        MapTemplate map = client.getCharacter().getCurrentMap();
        if(map == null) return;

        short lastValidCell = client.getCharacter().getCurrentCell();
        StringBuilder acceptedPath = new StringBuilder(path.length());
        try {
            for(int i = 0; i + 2 < path.length(); i += 3) {
                EOrientation orientation = Cell.decode(path.charAt(i));
                if(orientation == null) return;
                short cellId = Cell.decode(path.substring(i + 1, i + 3));
                boolean finalStep = (i + 3 >= path.length());
                if(cellId == lastValidCell || isValidPathCell(map, cellId, finalStep)) {
                    acceptedPath.append(path.charAt(i));
                    acceptedPath.append(path.charAt(i + 1));
                    acceptedPath.append(path.charAt(i + 2));
                    map.learnWalkableCell(cellId, "client_path");
                    lastValidCell = cellId;
                    continue;
                }

                if(map.isBlockingInteractiveCell(cellId)) {
                    blockedTargetCell = cellId;
                    break;
                }

                return;
            }
        } catch(Exception e) {
            return;
        }

        if(acceptedPath.length() == 0 && blockedTargetCell >= 0) {
            destinationCell = lastValidCell;
            valid = true;
            return;
        }

        effectivePath = acceptedPath.toString();
        destinationCell = lastValidCell;
        valid = hasMovementPrepared() || blockedTargetCell >= 0;
    }

    private boolean hasMovementPrepared() {
        return effectivePath != null && effectivePath.length() >= 3;
    }

    private boolean runPendingMapAction() {
        if(pendingActionCell < 0) return false;

        short cellId = pendingActionCell;
        int skillId = pendingActionSkill;
        pendingActionCell = -1;
        pendingActionSkill = -1;

        if(GroundItemService.pickup(client.getCharacter(), client.getSession(), cellId)) return true;
        InteractiveObjectService.use(client.getCharacter(), client.getSession(), cellId, skillId);
        return true;
    }

    private boolean runPendingFight() {
        if(pendingFightGroupId < 0) return false;
        int groupId = pendingFightGroupId;
        pendingFightGroupId = -1;
        FightParser.initiateFightVsMonsters(client.getCharacter(), client.getSession(), groupId);
        return true;
    }

    private void sendMovementCancel() {
        if(client != null && client.getSession() != null) {
            // Ancestra/Starloco style : GA;0 libère l'état d'action du client Flash.
            // BN seul peut laisser le client attendre une fin d'action et donc bloquer les clics suivants.
            client.getSession().write("GA;0");
        }
    }

    private boolean isValidPathCell(MapTemplate map, short cellId, boolean finalStep) {
        if(map == null || !map.isValidCellId(cellId)) return false;
        if(map.isBlockingInteractiveCell(cellId)) return false;

        if(!map.hasDecodedCells()) {
            // Sans les 560 cellules serveur, le client officiel reste la meilleure source :
            // s'il envoie la cellule dans son path, elle est marchable côté client.
            // On ne bloque que la destination finale occupée.
            return !finalStep || !map.isCellOccupied(cellId);
        }

        if(!finalStep) {
            MapTemplate.Cell cell = map.getCell(cellId);
            return cell != null && cell.isWalkable();
        }
        return map.isValidActorCell(cellId, true);
    }

    private boolean isValidDestination(short cellId) {
        MapTemplate map = client.getCharacter().getCurrentMap();
        if(map == null) return false;
        if(cellId == client.getCharacter().getCurrentCell()) return true;
        return isValidPathCell(map, cellId, true);
    }
}
