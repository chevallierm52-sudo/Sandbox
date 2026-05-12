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
 * Donnees des objets interactifs client, indexees par GFX layerObject2Num.
 * Table optionnelle : interactive_objects_data(id, respawn, duration, walkable, `Name IO`).
 */
public final class InteractiveObjectsData {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveObjectsData.class);
    private static final ConcurrentMap<Integer, InteractiveObjectTemplate> byGfx = new ConcurrentHashMap<Integer, InteractiveObjectTemplate>();

    private InteractiveObjectsData() {}

    public static void load() {
        byGfx.clear();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            loadFromTable(conn, "interactive_objects_data");
            logger.info("InteractiveObjectsData : {} objet(s) interactif(s) charges", byGfx.size());
        } catch(Exception e) {
            try {
                loadFromTable(conn, "interactive_object_data");
                logger.info("InteractiveObjectsData : {} objet(s) interactif(s) charges", byGfx.size());
            } catch(Exception second) {
                logger.warn("InteractiveObjectsData : table absente ou erreur, fallback decodeur map seul : {}", second.getMessage());
            }
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadFromTable(Connection conn, String table) throws Exception {
        byGfx.clear();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT id, respawn, duration, walkable, `Name IO` AS name FROM " + table)) {
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    String name = rs.getString("name");
                    InteractiveObjectTemplate template = new InteractiveObjectTemplate(
                            rs.getInt("id"),
                            rs.getInt("respawn"),
                            rs.getInt("duration"),
                            isWalkable(rs.getInt("walkable") != 0, name),
                            name);
                    byGfx.put(template.getGfxId(), template);
                }
            }
        }
    }

    private static boolean isWalkable(boolean databaseWalkable, String name) {
        if(databaseWalkable) return true;
        String normalized = normalizeName(name);
        if(normalized.isEmpty()) return false;

        // Les ressources recoltables 1.29 sont des objets interactifs visuels,
        // pas des obstacles de collision roleplay : on doit pouvoir traverser
        // les champs de ble/orge/avoine, etc. Les puits restent bloquants.
        if(normalized.contains("puits")) return false;
        if(normalized.contains("ble") || normalized.contains("orge") || normalized.contains("avoine")
                || normalized.contains("houblon") || normalized.contains("lin") || normalized.contains("seigle")
                || normalized.contains("riz") || normalized.contains("malt") || normalized.contains("frene")
                || normalized.contains("chataignier") || normalized.contains("noyer") || normalized.contains("chene")
                || normalized.contains("erable") || normalized.contains("if") || normalized.contains("merisier")
                || normalized.contains("ebene") || normalized.contains("charme") || normalized.contains("orme")
                || normalized.contains("bambou") || normalized.contains("bombu") || normalized.contains("oliviolet")
                || normalized.contains("menthe") || normalized.contains("orchidee") || normalized.contains("trefle")
                || normalized.contains("edelweiss") || normalized.contains("pandouille") || normalized.contains("poisson")
                || normalized.contains("pichon") || normalized.contains("dolomite") || normalized.contains("manganese")
                || normalized.contains("cuivre") || normalized.contains("argent") || normalized.contains("bronze")
                || normalized.contains("bauxite") || normalized.contains("silicate") || normalized.contains("fer")) {
            return true;
        }
        return false;
    }

    private static String normalizeName(String name) {
        if(name == null) return "";
        String s = name.toLowerCase(java.util.Locale.ROOT);
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}+", "");
        return s.replace('?', 'e');
    }

    public static InteractiveObjectTemplate get(int gfxId) {
        return byGfx.get(gfxId);
    }

    public static boolean isKnown(int gfxId) {
        return byGfx.containsKey(gfxId);
    }

    public static boolean isBlocking(int gfxId) {
        InteractiveObjectTemplate template = byGfx.get(gfxId);
        return template != null && !template.isWalkable();
    }

    public static final class InteractiveObjectTemplate {
        private final int gfxId;
        private final int respawnMs;
        private final int durationMs;
        private final boolean walkable;
        private final String name;

        private InteractiveObjectTemplate(int gfxId, int respawnMs, int durationMs, boolean walkable, String name) {
            this.gfxId = gfxId;
            this.respawnMs = respawnMs;
            this.durationMs = durationMs;
            this.walkable = walkable;
            this.name = name != null ? name : "";
        }

        public int getGfxId() { return gfxId; }
        public int getRespawnMs() { return respawnMs; }
        public int getDurationMs() { return durationMs; }
        public boolean isWalkable() { return walkable; }
        public String getName() { return name; }
    }
}
