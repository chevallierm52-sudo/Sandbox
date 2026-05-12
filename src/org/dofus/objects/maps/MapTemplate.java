package org.dofus.objects.maps;

import java.lang.Character;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.database.objects.InteractiveObjectCellsData;
import org.dofus.database.objects.InteractiveObjectsData;
import org.dofus.database.objects.MapCellWalkabilityData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.actors.NPC;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.utils.MapCellDecoder;
import org.dofus.utils.StringUtils;

public class MapTemplate {

    private int id;
    private byte abscissa, ordinate;
    private byte width, height;
    private short subarea;
    private String key;
    private String date;
    private boolean subscriberArea;
    private String places;

    private final HashMap<Short, TriggerTemplate> triggers = new HashMap<>();
    private final Map<Short, Cell> cells = new ConcurrentHashMap<>();
    private final List<Short> preferredSpawnCells = new ArrayList<Short>();

    private Map<Integer, Characters>    actors        = new ConcurrentHashMap<>();
    private final Map<Integer, NPC>     npcs          = new ConcurrentHashMap<>();
    private final Map<Integer, MonsterGroup> monsters  = new ConcurrentHashMap<>();
    private volatile boolean monsterGroupsSpawned = false;
 
    public MapTemplate(int id, byte abscissa, byte ordinate, byte width, byte height, short subarea, String key, String date, boolean subscriberArea, String places) {
        this.id = id;
        this.abscissa = abscissa;
        this.ordinate = ordinate;
        this.width = width;
        this.height = height;
        this.subarea = subarea;
        this.key = key;
        this.date = date;
        this.subscriberArea = subscriberArea;
        this.places = places;
        this.cells.putAll(MapCellDecoder.decode(key, date));
        this.preferredSpawnCells.addAll(MapCellDecoder.decodePlacementCells(places));
    }

    public int getId() {
        return id;
    }

    public byte getAbscissa() {
        return abscissa;
    }

    public byte getOrdinate() {
        return ordinate;
    }

    public byte getWidth() {
        return width;
    }

    public byte getHeight() {
        return height;
    }

    public short getSubarea() {
        return subarea;
    }

    public String getKey() {
        return key;
    }

    public String getDate() {
        return date;
    }

    public boolean isSubscriberArea() {
        return subscriberArea;
    }


    public Map<Short, Cell> getCells() {
        return cells;
    }

    public Cell getCell(short cellId) {
        return cells.get(cellId);
    }

    public boolean hasDecodedCells() {
        return !cells.isEmpty();
    }

    /**
     * Validation centrale d'une cellule roleplay.
     * Quand les donnees cellules sont decodees, on refuse les cases inactives/non marchables.
     * Quand elles ne le sont pas, on garde un fallback compatible : bornes + collisions.
     */
    public boolean isValidActorCell(short cellId) {
        return isValidActorCell(cellId, true);
    }

    public boolean isValidActorCell(short cellId, boolean allowTriggers) {
        if(!isValidCellId(cellId)) return false;
        if(!allowTriggers && triggers.containsKey(cellId)) return false;
        if(isBlockingInteractiveCell(cellId)) return false;
        if(hasDecodedCells()) {
            Cell cell = cells.get(cellId);
            if(cell == null || !cell.isWalkable()) return false;
        } else {
            Boolean override = MapCellWalkabilityData.getOverride(id, cellId);
            if(Boolean.FALSE.equals(override)) return false;
            // Sans donnees map completes, on reste jouable : les cellules inconnues sont acceptees
            // sauf si une collision explicite (puits, trigger interdit, occupation) les bloque.
        }
        return !isCellOccupied(cellId);
    }

    public boolean isBlockingInteractiveCell(short cellId) {
        if(!isValidCellId(cellId)) return false;
        if(InteractiveObjectCellsData.isBlocking(id, cellId)) return true;
        if(hasDecodedCells()) {
            Cell cell = cells.get(cellId);
            return cell != null && cell.hasBlockingInteractiveObject();
        }
        return false;
    }

    public void learnWalkableCell(short cellId, String reason) {
        if(!isValidCellId(cellId) || hasDecodedCells() || isBlockingInteractiveCell(cellId)) return;
        MapCellWalkabilityData.markWalkable(id, cellId, reason);
    }

    /** Cellule valide pour un groupe de monstres : pas de soleil/trigger et pas trop colle aux autres groupes. */
    public boolean isValidMonsterCell(short cellId) {
        if(!isValidActorCell(cellId, false)) return false;
        Cell cell = cells.get(cellId);
        if(cell != null && !cell.isMonsterSpawnable()) return false;
        return !isTooCloseToAnotherMonsterGroup(cellId, 3);
    }

    public boolean isCellOccupied(short cellId) {
        for(Characters actor : actors.values()) {
            if(actor != null && actor.getCurrentCell() == cellId) return true;
        }
        for(NPC npc : npcs.values()) {
            if(npc != null && npc.getCellId() == cellId) return true;
        }
        for(MonsterGroup group : monsters.values()) {
            if(group != null && group.getCell() == cellId) return true;
        }
        return false;
    }

    public Short findNearestValidMonsterCell(short origin) {
        Short bestCell = null;
        int bestScore = Integer.MAX_VALUE;

        Iterable<Short> candidates = getSpawnCandidates();
        for(Short candidate : candidates) {
            if(candidate == null || !isValidMonsterCell(candidate)) continue;

            int score = scoreMonsterSpawnCell(origin, candidate.shortValue());
            if(score < bestScore) {
                bestScore = score;
                bestCell = candidate;
            }
        }
        return bestCell;
    }

    private Iterable<Short> getSpawnCandidates() {
        if(hasDecodedCells()) {
            return new LinkedHashMap<Short, Cell>(cells).keySet();
        }
        java.util.List<Short> fallback = new java.util.ArrayList<Short>(560);
        for(short i = 0; i <= 559; i++) fallback.add(i);
        return fallback;
    }

    /**
     * Score de placement RP des groupes.
     * On ne choisit plus seulement la cellule numeriquement la plus proche : cela peut creer
     * une ligne artificielle de monstres sur le bord de la map. Les cellules de placement
     * combat de la map sont utilisees comme zones naturelles preferees, puis on elargit
     * autour d'elles avec une penalite progressive.
     */
    private int scoreMonsterSpawnCell(short origin, short candidate) {
        int preferredDistance = distanceToPreferredSpawnZone(candidate);

        /*
         * Quand map_templates.places est disponible, on privilégie fortement ces zones :
         * ce sont les cellules vertes/bleues/rouges de placement combat, donc souvent les
         * zones les plus naturelles et lisibles de la map. La cellule demandée en BDD ne
         * sert alors plus qu'à garder une cohérence locale, pas à forcer le placement.
         */
        int score = MapCellDecoder.distance(origin, candidate);
        if(preferredDistance >= 0) {
            score += preferredDistance * 24;
        }

        score += edgePenalty(candidate) * 8;
        score += crowdPenalty(candidate) * 12;
        return score;
    }

    private int distanceToPreferredSpawnZone(short cellId) {
        if(preferredSpawnCells.isEmpty()) return -1;
        int best = Integer.MAX_VALUE;
        for(Short preferred : preferredSpawnCells) {
            if(preferred == null) continue;
            int distance = MapCellDecoder.distance(cellId, preferred.shortValue());
            if(distance < best) best = distance;
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private int edgePenalty(short cellId) {
        Point p = MapCellDecoder.toPoint(cellId);
        int x = Math.abs(p.getX());
        int y = Math.abs(p.getY());

        int penalty = 0;
        if(x > 28) penalty += x - 28;
        if(y < 8) penalty += 8 - y;
        if(y > 60) penalty += y - 60;
        return penalty;
    }

    private int crowdPenalty(short cellId) {
        int penalty = 0;
        for(MonsterGroup group : monsters.values()) {
            if(group == null) continue;
            int distance = MapCellDecoder.distance(cellId, group.getCell());
            if(distance < 6) penalty += 6 - distance;
        }
        return penalty;
    }

    public Short findNearestValidActorCell(short origin, boolean allowTriggers) {
        Short bestCell = null;
        int bestDistance = Integer.MAX_VALUE;
        for(short candidate = 0; candidate <= 559; candidate++) {
            if(!isValidActorCell(candidate, allowTriggers)) continue;
            int distance = MapCellDecoder.distance(origin, candidate);
            if(distance < bestDistance) {
                bestDistance = distance;
                bestCell = candidate;
            }
        }
        return bestCell;
    }

    private boolean isTooCloseToAnotherMonsterGroup(short cellId, int minDistance) {
        for(MonsterGroup group : monsters.values()) {
            if(group == null) continue;
            if(MapCellDecoder.distance(cellId, group.getCell()) < minDistance) return true;
        }
        return false;
    }

    public boolean isValidCellId(short cellId) {
        return cellId >= 0 && cellId <= 559;
    }

    public String getPlaces() {
        return places;
    }

    public HashMap<Short, TriggerTemplate> getTriggers() {
        return triggers;
    }
	
    public void addTriggers(short cellId, TriggerTemplate trigger) {
    	if(!triggers.containsKey(cellId))
    		triggers.put(cellId, trigger);
    }
    
	public Map<Integer, Characters> getActors() {
		return actors;
	}
	
	public Characters getActor(int id) {
		return getActors().get(id);
	}

	public void setActors(Map<Integer, Characters> actors) {
		this.actors = actors;
	}
	
	public void addActor(Characters actor) {
		getActors().put(actor.getId(), actor);
	}
	
	public void removeActor(Characters actor) {
		getActors().remove(actor.getId());
	}

	public Map<Integer, NPC> getNpcs() { return npcs; }

	public void addNpc(NPC npc) {
		npcs.put(npc.getActorId(), npc);
	}

	/** Look up an NPC by its actor ID (spawnId + 100_000). */
	public NPC getNpc(int actorId) {
		return npcs.get(actorId);
	}

	// ── Monstres ──────────────────────────────────────────────────────────────

	public Map<Integer, MonsterGroup> getMonsterGroups() { return monsters; }

	public boolean areMonsterGroupsSpawned() { return monsterGroupsSpawned; }

	public void setMonsterGroupsSpawned(boolean monsterGroupsSpawned) {
		this.monsterGroupsSpawned = monsterGroupsSpawned;
	}

	public void addMonsterGroup(MonsterGroup group) {
		if(group != null) {
			group.setCurrentMap(this);
			monsters.put(group.getId(), group);
		}
	}

	public void removeMonsterGroup(MonsterGroup group) {
		monsters.remove(group.getId());
	}

	public static class TriggerTemplate {
		
	    private int id;
	    private short map;
	    private short cellId;
	    private short nextMap;
	    private short nextCellId;

	    public TriggerTemplate(int id, short map, short cellId, short nextMap, short nextCellId) {
	        this.id = id;
	        this.map = map;
	        this.cellId = cellId;
	        this.nextMap = nextMap;
	        this.nextCellId = nextCellId;
	    }

	    public int getId() {
	        return id;
	    }

	    public short getMap() {
	        return map;
	    }

	    public short getCellId() {
	        return cellId;
	    }

	    public short getNextMap() {
	        return nextMap;
	    }

	    public short getNextCellId() {
	        return nextCellId;
	    }
	}
	
	public static class Cell {
	    public static enum MovementType {
	        Unwalkable,
	        Door,
	        Trigger,
	        Walkable,
	        Paddock,
	        Road;

	        private static final HashMap<Integer, MovementType> e = new HashMap<>();
	        static {
	            for(MovementType type : MovementType.values())
	                e.put(type.ordinal(), type);
	        }

	        public static MovementType valueOf(Integer ordinal) {
	            return e.get(ordinal);
	        }
	    }

	    public static char encode(EOrientation orientation) {
	        return StringUtils.HASH.charAt(orientation.ordinal());
	    }

	    public static String encode(short cellId) {
	        return Character.toString(StringUtils.HASH.charAt(cellId / 64)) +
	               Character.toString(StringUtils.HASH.charAt(cellId % 64));
	    }

	    public static short decode(String cellCode) {
	        return (short) (StringUtils.HASH.indexOf(cellCode.charAt(0)) * 64 +
	                        StringUtils.HASH.indexOf(cellCode.charAt(1)));
	    }

	    public static EOrientation decode(char orientationCode) {
	        return EOrientation.valueOf(StringUtils.HASH.indexOf(orientationCode));
	    }

	    public static String encode(Collection<Node> nodes) {
	        StringBuilder sb = new StringBuilder(2 * nodes.size());
	        for(Node node : nodes) {
	            sb.append(encode(node.getDirection()));
	            sb.append(encode(node.getId()));
	        }
	        return sb.toString();
	    }

	    private short id;
	    private boolean active;
	    private boolean lineOfSight;
	    private MovementType movementType;
	    private int groundLevel;
	    private int groundSlope;
	    private int layerObject1Num;
	    private int layerObject2Num;
	    private boolean layerObject2Interactive;
	    private Point position;

	    public Cell(short id, boolean lineOfSight, MovementType movementType, int groundLevel, int groundSlope, Point position) {
	        this(id, true, lineOfSight, movementType, groundLevel, groundSlope, position);
	    }

	    public Cell(short id, boolean active, boolean lineOfSight, MovementType movementType, int groundLevel, int groundSlope, Point position) {
	        this(id, active, lineOfSight, movementType, groundLevel, groundSlope, 0, 0, false, position);
	    }

	    public Cell(short id, boolean active, boolean lineOfSight, MovementType movementType, int groundLevel,
	            int groundSlope, int layerObject1Num, int layerObject2Num, boolean layerObject2Interactive,
	            Point position) {
	        this.id = id;
	        this.active = active;
	        this.lineOfSight = lineOfSight;
	        this.movementType = movementType;
	        this.groundLevel = groundLevel;
	        this.groundSlope = groundSlope;
	        this.layerObject1Num = layerObject1Num;
	        this.layerObject2Num = layerObject2Num;
	        this.layerObject2Interactive = layerObject2Interactive;
	        this.position = position;
	    }

	    public short getId() {
	        return id;
	    }

	    public boolean isActive() {
	        return active;
	    }

	    public boolean isLineOfSight() {
	        return lineOfSight;
	    }

	    public MovementType getMovementType() {
	        return movementType;
	    }

	    public int getGroundLevel() {
	        return groundLevel;
	    }

	    public int getGroundSlope() {
	        return groundSlope;
	    }

	    public int getLayerObject1Num() {
	        return layerObject1Num;
	    }

	    public int getLayerObject2Num() {
	        return layerObject2Num;
	    }

	    public boolean isLayerObject2Interactive() {
	        return layerObject2Interactive;
	    }

	    public boolean isWalkable() {
	        return active && movementType != MovementType.Unwalkable;
	    }

	    public boolean hasInteractiveObject() {
	        return layerObject2Interactive && layerObject2Num > 0;
	    }

	    public boolean hasKnownInteractiveObject() {
	        return layerObject2Num > 0 && InteractiveObjectsData.isKnown(layerObject2Num);
	    }

	    public boolean hasBlockingInteractiveObject() {
	        return layerObject2Num > 0 && InteractiveObjectsData.isBlocking(layerObject2Num);
	    }

	    public boolean isMonsterSpawnable() {
	        return isWalkable()
	            && movementType != MovementType.Door
	            && movementType != MovementType.Trigger
	            && !hasInteractiveObject()
	            && !hasBlockingInteractiveObject();
	    }

	    public Point getPosition() {
	        return position;
	    }
	}
	
	public static class Point {
	    private int x, y;

	    public Point() {
	        this.x = 0;
	        this.y = 0;
	    }

	    public Point(int x, int y) {
	        this.x = x;
	        this.y = y;
	    }

	    public int getX() {
	        return x;
	    }

	    public void setX(int x) {
	        this.x = x;
	    }

	    public int getY() {
	        return y;
	    }

	    public void setY(int y) {
	        this.y = y;
	    }

	    @Override
	    public boolean equals(Object obj) {
	        if(obj instanceof Point)
	            return equals((Point) obj);
	        return false;
	    }

	    public boolean equals(Point point){
	        if(point == null)
	            return false;
	        return point.x == this.x && point.y == this.y;
	    }

	    @Override
	    public int hashCode() {
	        return x ^ y;
	    }

	    @Override
	    public String toString() {
	        return "(" + x + ";" + y + ")";
	    }
	}

	public class Node {
	    private short id;
	    private Node parent;
	    private EOrientation direction;
	    private Point position;
	    private boolean walkable;
	    private int g, h;

	    public Node(short id, Point position) {
	        this.id = id;
	        this.position = position;
	    }

	    public Node(short id, Point position, boolean walkable, EOrientation direction) {
	        this.id = id;
	        this.position = position;
	        this.walkable = walkable;
	        this.direction = direction;
	    }

	    public Node(Node parent, EOrientation direction, Cell cell) {
	        this.id = cell.getId();
	        this.parent = parent;
	        this.direction = direction;
	        this.position = cell.getPosition();
	        this.walkable = cell.isWalkable();
	    }

	    @Override
	    public boolean equals(Object obj) {
	        if(obj instanceof Node)
	            return equals((Node)obj);
	        return false;
	    }

	    @Override
	    public int hashCode() {
	        return id;
	    }

	    public boolean equals(Node node) {
	        if(node == null)
	            return false;
	        if(node.getId() == id)
	            return true;
	        return false;
	    }

	    public short getId() {
	        return id;
	    }

	    public Node getParent() {
	        return parent;
	    }

	    public EOrientation getDirection() {
	        return direction;
	    }

	    public Point getPosition() {
	        return position;
	    }

	    public boolean isWalkable() {
	        return walkable;
	    }

	    public int getF() {
	        return g + h;
	    }

	    public int getG() {
	        return g;
	    }

	    public void setG(int g) {
	        this.g = g;
	    }

	    public int getH() {
	        return h;
	    }

	    public void setH(int h) {
	        this.h = h;
	    }
	}
}
