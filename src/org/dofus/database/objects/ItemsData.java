package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 *   item_templates (id, name, type_id, level, pods, price, gfx_id, effects)
 *     effects : chaîne "#" séparée : "effectId,dice,min,max,special#..."
 *
 *   character_items (uid, owner_id, template_id, quantity, position, rolled_effects)
 *     rolled_effects : même format que effects
 *
 * TODO : créer les tables SQL dans item_system.sql
 */
public class ItemsData {

    private static final Logger logger = LoggerFactory.getLogger(ItemsData.class);

    /** Cache global des templates : templateId → ItemTemplate */
    private static final ConcurrentMap<Integer, ItemTemplate> templates = new ConcurrentHashMap<>();

    // ── Chargement global (au démarrage) ─────────────────────────────────────

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            loadTemplates(conn);
            logger.info("ItemsData : {} templates chargés", templates.size());
        } catch(Exception e) {
            logger.error("ItemsData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadTemplates(Connection conn) throws Exception {
        templates.clear();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, type_id, level, pods, price, gfx_id, effects FROM item_template");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                int    id      = rs.getInt("id");
                String name    = rs.getString("name");
                int    typeId  = rs.getInt("type_id");
                int    level   = rs.getInt("level");
                int    pods    = rs.getInt("weight");
                long   price   = rs.getLong("price");
                int    gfxId   = rs.getInt("gfx_id");
                String effects = rs.getString("effects");

                List<ItemEffect> fx = parseEffects(effects);
                templates.put(id, new ItemTemplate(id, name, typeId, level, pods, price, gfxId, fx));
            }
        }
    }

    // ── Chargement par personnage ─────────────────────────────────────────────

    /**
     * Charge l'inventaire d'un personnage depuis la BDD.
     *
     * @param characterId ID du personnage
     * @return liste d'items (vide si aucun ou erreur)
     */
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
                    long   uid        = rs.getLong("uid");
                    int    tplId      = rs.getInt("template_id");
                    int    qty        = rs.getInt("quantity");
                    int    pos        = rs.getInt("position");
                    String rolledFx   = rs.getString("rolled_effects");

                    ItemTemplate tpl = templates.get(tplId);
                    if(tpl == null) { logger.warn("ItemsData : template {} inconnu", tplId); continue; }

                    List<ItemEffect> fx = parseEffects(rolledFx);
                    result.add(new Item(uid, tpl, qty, pos, fx));
                }
            }
        }
        return result;
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────

    /** Insère un item en BDD (nouveau drop / craft). */
    public static void insert(int characterId, Item item) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO character_items (uid, owner_id, template_id, quantity, position, rolled_effects) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, item.getUid());
                ps.setInt (2, characterId);
                ps.setInt (3, item.getTemplate().getId());
                ps.setInt (4, item.getQuantity());
                ps.setInt (5, item.getPosition());
                ps.setString(6, buildEffectsString(item.getRolledEffects()));
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("ItemsData.insert failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    /** Met à jour la quantité et la position d'un item existant. */
    public static void update(Item item) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "UPDATE character_items SET quantity=?, position=? WHERE uid=?")) {
                ps.setInt (1, item.getQuantity());
                ps.setInt (2, item.getPosition());
                ps.setLong(3, item.getUid());
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("ItemsData.update failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    /** Supprime un item de la BDD. */
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

    // ── Accès aux templates ───────────────────────────────────────────────────

    public static ItemTemplate getTemplate(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, ItemTemplate> getTemplates() { return templates; }

    // ── Utilitaires de parsing ────────────────────────────────────────────────

    /** Parse une chaîne d'effets : {@code effectId,dice,min,max,special#...} */
    static List<ItemEffect> parseEffects(String raw) {
        List<ItemEffect> fx = new ArrayList<>();
        if(raw == null || raw.trim().isEmpty()) return fx;
        for(String part : raw.split("#")) {
            String[] f = part.split(",", 5);
            if(f.length < 5) continue;
            try {
                fx.add(new ItemEffect(
                    Integer.parseInt(f[0].trim()),
                    Integer.parseInt(f[1].trim()),
                    Integer.parseInt(f[2].trim()),
                    Integer.parseInt(f[3].trim()),
                    Integer.parseInt(f[4].trim())
                ));
            } catch(NumberFormatException e) {
                logger.debug("ItemsData : effet malformé ignoré : {}", part);
            }
        }
        return fx;
    }

    private static String buildEffectsString(List<ItemEffect> effects) {
        if(effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(ItemEffect e : effects) {
            if(sb.length() > 0) sb.append('#');
            sb.append(e.getEffectId()).append(',')
              .append(e.getDice()).append(',')
              .append(e.getMin()).append(',')
              .append(e.getMax()).append(',')
              .append(e.getSpecial());
        }
        return sb.toString();
    }
}
