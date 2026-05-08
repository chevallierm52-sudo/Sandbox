package org.dofus.objects.maps;

import java.lang.Character;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
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
    
    private Map<Integer, Characters> actors = new ConcurrentHashMap<>();
 
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
	    private boolean lineOfSight;
	    private MovementType movementType;
	    private int groundLevel;
	    private int groundSlope;
	    private Point position;

	    public Cell(short id, boolean lineOfSight, MovementType movementType, int groundLevel, int groundSlope, Point position) {
	        this.id = id;
	        this.lineOfSight = lineOfSight;
	        this.movementType = movementType;
	        this.groundLevel = groundLevel;
	        this.groundSlope = groundSlope;
	        this.position = position;
	    }

	    public short getId() {
	        return id;
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

	    public boolean isWalkable() {
	        return movementType != MovementType.Unwalkable;
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
