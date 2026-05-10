package org.dofus.objects.actors;

import org.dofus.objects.maps.MapTemplate;

/**
 * A single NPC instance placed on a map.
 * Actor ID = spawn DB id + 100_000 to avoid collision with character IDs.
 */
public class NPC implements IActor {

	private final int         spawnId;   // DB primary key of npc_spawns row
	private final NpcTemplate template;
	private final MapTemplate map;
	private final short       cell;
	private final EOrientation orientation;

	public NPC(int spawnId, NpcTemplate template, MapTemplate map, short cell, EOrientation orientation) {
		this.spawnId     = spawnId;
		this.template    = template;
		this.map         = map;
		this.cell        = cell;
		this.orientation = orientation;
	}

	/** Unique actor ID on the map (offset from spawn ID to avoid character ID collision). */
	@Override public int          getActorId()   { return spawnId + 100_000; }
	@Override public int          getActorType()  { return -4; }
	@Override public EOrientation getOrientation(){ return orientation; }
	@Override public MapTemplate  getMapId()      { return map; }
	@Override public int          getCellId()     { return cell; }

	public int         getSpawnId()  { return spawnId; }
	public NpcTemplate getTemplate() { return template; }

	@Override
	public int getId() {
		// TODO Auto-generated method stub
		return 0;
	}
}
