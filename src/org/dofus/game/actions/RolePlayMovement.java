package org.dofus.game.actions;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.MapsData;
import org.dofus.network.game.GameClient;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;

public class RolePlayMovement implements IGameAction {

	private final GameClient client;
    private final String path;
    
	public RolePlayMovement(String path, GameClient client) {
		this.path = path;
		this.client = client;
	}
	
	public static void teleport(GameClient client, MapTemplate nextMap, short cellId) {
        if(nextMap == null)
        	return;

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
		String movePacket = "GA1;1;" + client.getCharacter().getId() + ";" + this.getPath();
		for(Characters actors : client.getCharacter().getCurrentMap().getActors().values()) {
			IoSession actorSession = WorldData.getSessionByAccount().get(actors.getOwner());
			if(actorSession == null || !actorSession.isConnected())
				continue;
			actorSession.write(movePacket);
		}
	}

	@Override
	public void end() {
		if(path == null || path.length() < 3)
			return;

		EOrientation orientation = Cell.decode(path.charAt(path.length() - 3));
        short cellId = Cell.decode(path.substring(path.length() - 2));

        client.getCharacter().setCurrentOrientation(orientation);
        client.getCharacter().setCurrentCell(cellId);

        TriggerTemplate trigger = client.getCharacter().getCurrentMap().getTriggers().get(cellId);

        if(trigger != null)
            teleport(client, MapsData.load(trigger.getNextMap()), trigger.getNextCellId());
	}

	@Override
	public void cancel() {
		
	}

    public void cancel(short cellId) {
    	client.getCharacter().setCurrentCell(cellId);
        cancel();
    }
    
    public String getPath() {
        return "a" + Cell.encode(client.getCharacter().getCurrentCell()) + path;
    }
}
