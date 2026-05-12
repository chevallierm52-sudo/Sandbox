package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemEffect;
import org.dofus.objects.items.ItemTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chargement et persistance des objets Dofus 1.29.
 *
 * Tables attendues :
 * item_template (id, name, type_id, level, weight, price, gfx_id, effects)
 * character_items (uid, owner_id, template_id, quantity, position, rolled_effects)
 */
public class ItemsData {

    private static final Logger logger = LoggerFactory.getLogger(ItemsData.class);
    private static final ConcurrentMap<Integer, ItemTemplate> templates = new ConcurrentHashMap<>();

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            loadTemplates(conn);
            logger.info("ItemsData : {} templates charges", templates.size());
        } catch(Exception e) {
            logger.error("ItemsData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadTemplates(Connection conn) throws Exception {
        templates.clear();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM item_template");
            ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            while(rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                int typeId = rs.getInt("type_id");
                int level = rs.getInt("level");
                int pods = rs.getInt("weight");
                long price = rs.getLong("price");
                int gfxId = rs.getInt("gfx_id");
                List<ItemEffect> fx = parseEffects(rs.getString("effects"));

                String conditions = optionalString(rs, meta, "conditions", "condition", "criteria", "criterions");
                boolean twoHanded = optionalBoolean(rs, meta, "two_handed", "is_two_handed", "isTwoHanded");

                templates.put(id, new ItemTemplate(id, name, typeId, level, pods, price, gfxId, fx, conditions, twoHanded));
            }
        }
    }

    public static List<Item> loadForCharacter(int characterId) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            return loadItems(conn, characterId);
        } catch(Exception e) {
            logger.error("ItemsData.loadForCharacter({}) failed: {}", characterId, e.getMessage());
            return Collections.emptyList();
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static List<Item> loadItems(Connection conn, int characterId) throws Exception {
        List<Item> result = new ArrayList<>();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT uid, template_id, quantity, position, rolled_effects " +
                "FROM character_items WHERE owner_id = ?")) {
            ps.setInt(1, characterId);
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    long uid = rs.getLong("uid");
                    int tplId = rs.getInt("template_id");
                    int qty = rs.getInt("quantity");
                    int pos = rs.getInt("position");

                    ItemTemplate tpl = templates.get(tplId);
                    if(tpl == null) {
                        logger.warn("ItemsData : template {} inconnu", tplId);
                        continue;
                    }

                    List<ItemEffect> fx = parseEffects(rs.getString("rolled_effects"));
                    Item item = new Item(uid, tpl, qty, pos, fx);
                    if(item.hasUnrolledEffects()) {
                        item.rerollEffectsFromTemplate();
                        update(item);
                    }
                    result.add(item);
                }
            }
        }
        return result;
    }

    public static void insert(int characterId, Item item) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO character_items (uid, owner_id, template_id, quantity, position, rolled_effects) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, item.getUid());
                ps.setInt(2, characterId);
                ps.setInt(3, item.getTemplate().getId());
                ps.setInt(4, item.getQuantity());
                ps.setInt(5, item.getPosition());
                ps.setString(6, buildEffectsString(item.getRolledEffects()));
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("ItemsData.insert failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static void update(Item item) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "UPDATE character_items SET template_id=?, quantity=?, position=?, rolled_effects=? WHERE uid=?")) {
                ps.setInt(1, item.getTemplate().getId());
                ps.setInt(2, item.getQuantity());
                ps.setInt(3, item.getPosition());
                ps.setString(4, buildEffectsString(item.getRolledEffects()));
                ps.setLong(5, item.getUid());
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("ItemsData.update failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static void delete(long uid) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM character_items WHERE uid=?")) {
                ps.setLong(1, uid);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("ItemsData.delete failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static ItemTemplate getTemplate(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, ItemTemplate> getTemplates() { return templates; }

    private static String optionalString(ResultSet rs, ResultSetMetaData meta, String... columnNames) throws Exception {
        for(String column : columnNames) {
            if(hasColumn(meta, column)) {
                String value = rs.getString(column);
                return value != null ? value : "";
            }
        }
        return "";
    }

    private static boolean optionalBoolean(ResultSet rs, ResultSetMetaData meta, String... columnNames) throws Exception {
        for(String column : columnNames) {
            if(hasColumn(meta, column)) {
                String value = rs.getString(column);
                return value != null && ("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()) || "yes".equalsIgnoreCase(value.trim()));
            }
        }
        return false;
    }

    private static boolean hasColumn(ResultSetMetaData meta, String columnName) throws Exception {
        for(int i = 1; i <= meta.getColumnCount(); i++) {
            if(columnName.equalsIgnoreCase(meta.getColumnLabel(i)) || columnName.equalsIgnoreCase(meta.getColumnName(i))) {
                return true;
            }
        }
        return false;
    }

    static List<ItemEffect> parseEffects(String raw) {
        List<ItemEffect> fx = new ArrayList<>();
        if(raw == null || raw.trim().isEmpty()) return fx;

        String trimmed = raw.trim();
        boolean dofusFormat = trimmed.indexOf('#') >= 0
            && (trimmed.indexOf(',') < 0 || trimmed.substring(0, trimmed.indexOf(',')).indexOf('#') >= 0);

        if(dofusFormat) {
            for(String part : trimmed.split(",")) parseDofusEffect(fx, part);
        } else {
            for(String part : trimmed.split("#")) parseLegacyEffect(fx, part);
        }
        return fx;
    }

    private static void parseDofusEffect(List<ItemEffect> fx, String part) {
        String[] f = part.split("#", 5);
        if(f.length < 4) return;
        try {
            fx.add(new ItemEffect(
                parseHex(f[0]),
                parseHex(f[1]),
                parseHex(f[2]),
                parseHex(f[3]),
                f.length >= 5 ? f[4].trim() : "0"
            ));
        } catch(NumberFormatException e) {
            logger.debug("ItemsData : effet Dofus malforme ignore : {}", part);
        }
    }

    private static void parseLegacyEffect(List<ItemEffect> fx, String part) {
        String[] f = part.split(",", 5);
        if(f.length < 5) return;
        try {
            fx.add(new ItemEffect(
                Integer.parseInt(f[0].trim()),
                Integer.parseInt(f[1].trim()),
                Integer.parseInt(f[2].trim()),
                Integer.parseInt(f[3].trim()),
                f[4].trim()
            ));
        } catch(NumberFormatException e) {
            logger.debug("ItemsData : effet legacy malforme ignore : {}", part);
        }
    }

    private static int parseHex(String value) {
        String clean = value == null ? "" : value.trim();
        if(clean.isEmpty()) return 0;
        return Integer.parseInt(clean, 16);
    }

    private static String buildEffectsString(List<ItemEffect> effects) {
        if(effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(ItemEffect e : effects) {
            if(sb.length() > 0) sb.append(',');
            sb.append(e.toDofusString());
        }
        return sb.toString();
    }
}
