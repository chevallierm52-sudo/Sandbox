package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;

public class MapsData {

	private static final Logger logger = LoggerFactory.getLogger(MapsData.class);

	private static final ConcurrentMap<Integer, MapTemplate>      maps     = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Integer, TriggerTemplate>  triggers = new ConcurrentHashMap<>();

	public static MapTemplate load(int mapId) {
		if(maps.containsKey(mapId))
			return maps.get(mapId);

		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"SELECT * FROM `map_templates` WHERE `id` = ?");
			stmt.setInt(1, mapId);
			try(ResultSet reader = stmt.executeQuery()) {
				if(reader.next()) {
					MapTemplate map = new MapTemplate(
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
			} finally {
				stmt.close();
			}
		} catch(SQLException e) {
			logger.error("Impossible to load map {}: {}", mapId, e.getMessage());
		} finally {
			Connector.release(conn);
		}
		return null;
	}

	public static void loadTriggers(MapTemplate map) {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"SELECT * FROM `map_triggers` WHERE `map` = ?");
			stmt.setInt(1, map.getId());
			try(ResultSet reader = stmt.executeQuery()) {
				while(reader.next()) {
					if(!triggers.containsKey(reader.getInt("id"))) {
						TriggerTemplate trigger = new TriggerTemplate(
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
			} finally {
				stmt.close();
			}
		} catch(SQLException e) {
			logger.error("Impossible to load triggers for map {}: {}", map.getId(), e.getMessage());
		} finally {
			Connector.release(conn);
		}
	}

	public static MapTemplate findById(int mapId) {
		if(maps.containsKey(mapId))
			return maps.get(mapId);
		return load(mapId);
	}

	public static MapTemplate findByCoord(int x, int y) {
		for(MapTemplate map : maps.values())
			if(map.getAbscissa() == x && map.getOrdinate() == y)
				return map;

		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
				"SELECT id FROM `map_templates` WHERE `abscissa` = ? AND `ordinate` = ? LIMIT 1");
			stmt.setInt(1, x);
			stmt.setInt(2, y);
			try(ResultSet reader = stmt.executeQuery()) {
				if(reader.next())
					return load(reader.getInt("id"));
			} finally {
				stmt.close();
			}
		} catch(SQLException e) {
			logger.error("findByCoord error ({},{}): {}", new Object[]{x, y, e.getMessage()});
		} finally {
			Connector.release(conn);
		}
		return null;
	}
}
