package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overrides/learning serveur pour les cellules roleplay quand les 560 cellules
 * de map ne sont pas disponibles cote BDD. Le client 1.29 ne propose normalement
 * que des chemins possibles : on apprend donc les cellules parcourues comme
 * walkables, tout en laissant les objets explicitement bloquants prendre le dessus.
 */
public final class MapCellWalkabilityData {

    private static final Logger logger = LoggerFactory.getLogger(MapCellWalkabilityData.class);
    private static final ConcurrentMap<String, Boolean> overrides = new ConcurrentHashMap<String, Boolean>();

    private MapCellWalkabilityData() {}

    public static void load() {
        overrides.clear();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureTable(conn);
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT map_id, cell_id, walkable FROM map_cell_walkability");
                ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    put(rs.getInt("map_id"), rs.getShort("cell_id"), rs.getInt("walkable") != 0);
                }
            }
            logger.info("MapCellWalkabilityData : {} override(s) charge(s)", overrides.size());
        } catch(Exception e) {
            logger.warn("MapCellWalkabilityData : table indisponible, fallback auto : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void ensureTable(Connection conn) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS map_cell_walkability ("
              + "map_id INT NOT NULL,"
              + "cell_id SMALLINT NOT NULL,"
              + "walkable TINYINT NOT NULL DEFAULT 1,"
              + "reason VARCHAR(64) NOT NULL DEFAULT 'learned',"
              + "PRIMARY KEY(map_id, cell_id))")) {
            ps.executeUpdate();
        }
    }

    public static Boolean getOverride(int mapId, short cellId) {
        return overrides.get(key(mapId, cellId));
    }

    public static boolean isKnown(int mapId, short cellId) {
        return overrides.containsKey(key(mapId, cellId));
    }

    public static void markWalkable(int mapId, short cellId, String reason) {
        learn(mapId, cellId, true, reason);
    }

    public static void markBlocked(int mapId, short cellId, String reason) {
        learn(mapId, cellId, false, reason);
    }

    private static void learn(int mapId, short cellId, boolean walkable, String reason) {
        if(mapId <= 0 || cellId < 0 || cellId > 559) return;
        String k = key(mapId, cellId);
        Boolean previous = overrides.putIfAbsent(k, walkable);
        if(previous != null && previous.booleanValue() == walkable) return;
        if(previous != null && previous.booleanValue() != walkable) overrides.put(k, walkable);

        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureTable(conn);
            try(PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO map_cell_walkability (map_id, cell_id, walkable, reason) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, mapId);
                ps.setShort(2, cellId);
                ps.setInt(3, walkable ? 1 : 0);
                ps.setString(4, reason == null ? "learned" : reason);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.debug("MapCellWalkabilityData.learn ignored: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void put(int mapId, short cellId, boolean walkable) {
        if(mapId > 0 && cellId >= 0 && cellId <= 559) overrides.put(key(mapId, cellId), walkable);
    }

    private static String key(int mapId, short cellId) {
        return mapId + ":" + cellId;
    }
}
