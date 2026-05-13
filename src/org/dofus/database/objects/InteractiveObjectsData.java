package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
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
    private static final Map<Integer, OfficialInteractiveObject> OFFICIAL_BY_GFX = officialObjects();

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
                "SELECT id, respawn, duration, unknow, walkable, `Name IO` AS name FROM " + table)) {
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    String name = rs.getString("name");
                    OfficialInteractiveObject official = OFFICIAL_BY_GFX.get(rs.getInt("id"));
                    InteractiveObjectTemplate template = new InteractiveObjectTemplate(
                            rs.getInt("id"),
                            rs.getInt("respawn"),
                            rs.getInt("duration"),
                            rs.getInt("unknow"),
                            official != null ? official.walkable : isWalkable(rs.getInt("walkable") != 0, name),
                            name,
                            official != null ? official.objectId : -1,
                            official != null ? official.typeId : -1,
                            official != null ? official.skills : Collections.<Integer>emptySet());
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
        // Arbres (bucheronnage), minerais et poissons restent bloquants meme ici.
        if(normalized.contains("puits")) return false;
        if(normalized.contains("ble") || normalized.contains("orge") || normalized.contains("avoine")
                || normalized.contains("houblon") || normalized.contains("lin") || normalized.contains("seigle")
                || normalized.contains("riz") || normalized.contains("malt") || normalized.contains("chanvre")
                || normalized.contains("menthe") || normalized.contains("orchidee") || normalized.contains("trefle")
                || normalized.contains("edelweiss") || normalized.contains("pandouille")) {
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

    public static boolean isWalkableObject(int gfxId) {
        InteractiveObjectTemplate template = byGfx.get(gfxId);
        return template != null && template.isWalkable();
    }

    public static boolean isSkillAllowed(int gfxId, int skillId) {
        InteractiveObjectTemplate template = byGfx.get(gfxId);
        return template == null || template.acceptsSkill(skillId);
    }

    private static Map<Integer, OfficialInteractiveObject> officialObjects() {
        Map<Integer, OfficialInteractiveObject> byGfx = new HashMap<Integer, OfficialInteractiveObject>();
        put(byGfx, 7500, 1, 1, false, 6);
        put(byGfx, 7003, 2, 2, false, 101);
        put(byGfx, 7503, 8, 1, false, 10);
        put(byGfx, 7008, 12, 2, false, 11, 12);
        put(byGfx, 7009, 12, 2, false, 11, 12);
        put(byGfx, 7010, 12, 2, false, 11, 12);
        put(byGfx, 7000, 16, 3, false, 114, 44);
        put(byGfx, 7026, 16, 3, false, 114, 44);
        put(byGfx, 7029, 16, 3, false, 114, 44);
        put(byGfx, 4287, 16, 3, false, 114, 44);
        put(byGfx, 7520, 17, 1, false, 24);
        put(byGfx, 7001, 22, 2, false, 109, 27);
        put(byGfx, 7526, 24, 1, false, 29);
        put(byGfx, 7527, 25, 1, false, 30);
        put(byGfx, 7528, 26, 1, false, 31);
        put(byGfx, 7002, 27, 2, false, 32);
        put(byGfx, 7505, 28, 1, false, 33);
        put(byGfx, 7507, 29, 1, false, 34);
        put(byGfx, 7509, 30, 1, false, 35);
        put(byGfx, 7504, 31, 1, false, 37);
        put(byGfx, 7508, 32, 1, false, 38);
        put(byGfx, 7501, 33, 1, false, 39);
        put(byGfx, 7502, 34, 1, false, 40);
        put(byGfx, 7506, 35, 1, false, 41);
        put(byGfx, 7525, 37, 1, false, 28);
        put(byGfx, 7511, 38, 1, true, 45);
        put(byGfx, 7512, 39, 1, true, 46);
        put(byGfx, 7005, 41, 2, false, 48);
        put(byGfx, 7513, 42, 1, true, 50, 68);
        put(byGfx, 7515, 43, 1, true, 53);
        put(byGfx, 7516, 44, 1, true, 52);
        put(byGfx, 7517, 45, 1, true, 57);
        put(byGfx, 7514, 46, 1, true, 54, 69);
        put(byGfx, 7518, 47, 1, true, 58);
        put(byGfx, 7510, 48, 1, false, 42);
        put(byGfx, 7521, 52, 1, false, 55);
        put(byGfx, 7522, 53, 1, false, 25);
        put(byGfx, 7524, 54, 1, false, 56);
        put(byGfx, 7523, 55, 1, false, 26);
        put(byGfx, 7536, 61, 1, true, 74);
        put(byGfx, 7534, 66, 1, true, 72);
        put(byGfx, 7533, 67, 1, true, 71);
        put(byGfx, 7535, 68, 1, true, 73);
        put(byGfx, 7530, 71, 1, false, 128);
        put(byGfx, 7532, 74, 1, false, 125);
        put(byGfx, 7529, 75, 1, false, 124);
        put(byGfx, 7537, 76, 1, false, 126);
        put(byGfx, 7531, 77, 1, false, 129);
        put(byGfx, 7538, 78, 1, false, 130);
        put(byGfx, 7539, 79, 1, false, 127);
        put(byGfx, 7540, 81, 1, false, 131);
        put(byGfx, 7519, 84, 1, false, 102);
        put(byGfx, 7350, 85, 6, false, 106, 104, 105);
        put(byGfx, 7351, 85, 6, false, 106, 104, 105);
        put(byGfx, 7352, 105, 6, false, 153);
        put(byGfx, 7353, 85, 6, false, 106, 104, 105);
        put(byGfx, 7541, 98, 1, false, 139);
        put(byGfx, 7543, 99, 1, false, 140);
        put(byGfx, 7544, 100, 1, false, 136);
        put(byGfx, 7542, 101, 1, false, 141);
        put(byGfx, 7030, 106, 10, false, 157);
        put(byGfx, 7031, 106, 10, false, 157);
        put(byGfx, 7553, 108, 1, false, 154);
        put(byGfx, 7554, 109, 1, false, 155);
        put(byGfx, 7552, 110, 1, false, 158);
        put(byGfx, 7550, 111, 1, true, 159);
        put(byGfx, 7551, 112, 1, true, 160);
        put(byGfx, 7555, 113, 1, false, 161);
        put(byGfx, 7556, 114, 1, false, 162);
        put(byGfx, 7557, 121, 1, false, 174);
        return Collections.unmodifiableMap(byGfx);
    }

    private static void put(Map<Integer, OfficialInteractiveObject> byGfx, int gfxId, int objectId, int typeId, boolean walkable, int... skills) {
        Set<Integer> allowed = new HashSet<Integer>();
        for(int skill : skills) allowed.add(skill);
        byGfx.put(gfxId, new OfficialInteractiveObject(objectId, typeId, walkable, Collections.unmodifiableSet(allowed)));
    }

    private static final class OfficialInteractiveObject {
        private final int objectId;
        private final int typeId;
        private final boolean walkable;
        private final Set<Integer> skills;

        private OfficialInteractiveObject(int objectId, int typeId, boolean walkable, Set<Integer> skills) {
            this.objectId = objectId;
            this.typeId = typeId;
            this.walkable = walkable;
            this.skills = skills;
        }
    }

    public static final class InteractiveObjectTemplate {
        private final int gfxId;
        private final int respawnMs;
        private final int durationMs;
        private final int unknown;
        private final boolean walkable;
        private final String name;
        private final int officialObjectId;
        private final int officialTypeId;
        private final Set<Integer> skillIds;

        private InteractiveObjectTemplate(int gfxId, int respawnMs, int durationMs, int unknown, boolean walkable, String name,
                int officialObjectId, int officialTypeId, Set<Integer> skillIds) {
            this.gfxId = gfxId;
            this.respawnMs = respawnMs;
            this.durationMs = durationMs;
            this.unknown = unknown;
            this.walkable = walkable;
            this.name = name != null ? name : "";
            this.officialObjectId = officialObjectId;
            this.officialTypeId = officialTypeId;
            this.skillIds = skillIds != null ? skillIds : Collections.<Integer>emptySet();
        }

        public int getGfxId() { return gfxId; }
        public int getRespawnMs() { return respawnMs; }
        public int getDurationMs() { return durationMs; }
        public int getUnknown() { return unknown; }
        public boolean isWalkable() { return walkable; }
        public String getName() { return name; }
        public int getOfficialObjectId() { return officialObjectId; }
        public int getOfficialTypeId() { return officialTypeId; }
        public boolean acceptsSkill(int skillId) { return skillId < 0 || skillIds.isEmpty() || skillIds.contains(skillId); }
    }
}
