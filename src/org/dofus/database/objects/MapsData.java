package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;

public class MapsData {

	private static final Connection connection = Connector.getConnection();
	
	private static ConcurrentMap<Integer, MapTemplate> maps = new ConcurrentHashMap<Integer, MapTemplate>();
	private static ConcurrentMap<Integer, TriggerTemplate> triggers = new ConcurrentHashMap<Integer, TriggerTemplate>();
	
	public static MapTemplate load(int mapId) {
		if(!maps.containsKey(mapId)) {
			try {
	            ResultSet reader = connection
	            		.createStatement()
	            		.executeQuery("SELECT * FROM `map_templates` WHERE `id`='" + mapId + "';");
	            
	            MapTemplate map;
	            
	            for(MapTemplate mapt : maps.values())
	            	if(mapt.getId() == mapId)
	            		return mapt;
	            
	            while(reader.next()) {
	            	map = new MapTemplate(
	            			reader.getInt("id"),
	            			reader.getByte("abscissa"),
	            			reader.getByte("ordinate"),
	            			reader.getByte("width"),
	            			reader.getByte("height"),
	            			reader.getShort("subarea"),
	                        reader.getString("key"),
	                        reader.getString("date"),
	                        reader.getBoolean("subscriberArea"),
	                        reader.getString("places")
	            			);
	           	
	            	maps.put(map.getId(), map);
	            	loadTriggers(map);
	            	return map;
	            }
	        } catch(SQLException e) {
	            e.getMessage();
	        }
			return null;	
		} else
			return maps.get(mapId);
	}
	
	public static void loadTriggers(MapTemplate map) {
		try {
            ResultSet reader = connection
            		.createStatement()
            		.executeQuery("SELECT * FROM `map_triggers` WHERE `map`='" + map.getId() + "';");
            
            TriggerTemplate trigger;
            
            while(reader.next()) {
            	if(!triggers.containsKey(reader.getInt("id"))) {
            		trigger = new TriggerTemplate(
                			reader.getInt("id"),
                			reader.getShort("map"),
                            reader.getShort("cell"),
                            reader.getShort("nextMap"),
                            reader.getShort("nextCell")
                			);
            		
            		triggers.put(trigger.getId(), trigger);
            		map.addTriggers(trigger.getCellId(), trigger);
            	}
            }
        } catch(SQLException e) {
            System.out.println("Impossible to load trigger "+ e.getMessage());
        }
	}
	
	public static MapTemplate findById(int mapId) {
		if(maps.containsKey(mapId))
			return maps.get(mapId);
		else
			return load(mapId);
	}
}
