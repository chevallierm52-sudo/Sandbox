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
 * Cellules portant un objet interactif quand les donnees map ne contiennent pas
 * les 560 cellules decodees. Les objets appris ici servent surtout a empecher
 * les deplacements sur les puits/ressources visibles cote client.
 */
public final class InteractiveObjectCellsData {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveObjectCellsData.class);
    private static final ConcurrentMap<String, InteractiveCell> cells = new ConcurrentHashMap<String, InteractiveCell>();

    private InteractiveObjectCellsData() {}

    public static void load() {
        cells.clear();
        seedKnownRetroCells();

        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureTable(conn);
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT map_id, cell_id, skill_id, blocking FROM interactive_object_cells");
                ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    int skillId = rs.getInt("skill_id");
                    boolean blocking = rs.getInt("blocking") != 0 && isBlockingSkill(skillId);
                    put(rs.getInt("map_id"), rs.getShort("cell_id"), skillId, blocking);
                }
            }
            logger.info("InteractiveObjectCellsData : {} cellule(s) chargee(s)", cells.size());
        } catch(Exception e) {
            logger.warn("InteractiveObjectCellsData : table indisponible, seeds seuls : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void ensureTable(Connection conn) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS interactive_object_cells ("
              + "map_id INT NOT NULL,"
              + "cell_id SMALLINT NOT NULL,"
              + "skill_id INT NOT NULL DEFAULT -1,"
              + "blocking TINYINT NOT NULL DEFAULT 1,"
              + "PRIMARY KEY(map_id, cell_id))")) {
            ps.executeUpdate();
        }
    }

    private static void seedKnownRetroCells() {
        // Puits visibles connus : sans donnees cellules decodees, ils doivent quand meme bloquer le deplacement.
        put(1359, (short) 283, 102, true);
        put(7456, (short) 283, 102, true);
    }

    public static void learn(int mapId, short cellId, int skillId, boolean blocking) {
        if(mapId <= 0 || cellId < 0 || cellId > 559) return;
        blocking = blocking && isBlockingSkill(skillId);
        put(mapId, cellId, skillId, blocking);

        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureTable(conn);
            try(PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO interactive_object_cells (map_id, cell_id, skill_id, blocking) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, mapId);
                ps.setShort(2, cellId);
                ps.setInt(3, skillId);
                ps.setInt(4, blocking ? 1 : 0);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.debug("InteractiveObjectCellsData.learn ignored: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static boolean isKnown(int mapId, short cellId) {
        return cells.containsKey(key(mapId, cellId));
    }

    public static boolean isBlocking(int mapId, short cellId) {
        InteractiveCell cell = cells.get(key(mapId, cellId));
        return cell != null && cell.blocking && isBlockingSkill(cell.skillId);
    }

    private static boolean isBlockingSkill(int skillId) {
        // Skills explicitement bloquants connus en 1.29 sandbox.
        // 102 = puits dans les logs. Les recoltes (ble, bois, minerais, plantes, poissons)
        // doivent rester traversables meme si une ancienne ligne BDD les a stockees blocking=1.
        return skillId == 102;
    }

    private static void put(int mapId, short cellId, int skillId, boolean blocking) {
        cells.put(key(mapId, cellId), new InteractiveCell(mapId, cellId, skillId, blocking));
    }

    private static String key(int mapId, short cellId) {
        return mapId + ":" + cellId;
    }

    private static final class InteractiveCell {
        private final int mapId;
        private final short cellId;
        private final int skillId;
        private final boolean blocking;

        private InteractiveCell(int mapId, short cellId, int skillId, boolean blocking) {
            this.mapId = mapId;
            this.cellId = cellId;
            this.skillId = skillId;
            this.blocking = blocking;
        }
    }
}
