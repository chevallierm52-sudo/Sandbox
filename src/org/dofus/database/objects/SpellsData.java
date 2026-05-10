package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.spells.SpellTemplate;
import org.dofus.objects.spells.SpellTemplate.SpellEffect;
import org.dofus.objects.spells.SpellTemplate.SpellLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chargement des sorts Dofus 1.29.
 *
 * Tables attendues :
 *   spell_templates (id, name, sprit_id)
 *   spell_levels    (spell_id, level, ap_cost, min_range, max_range,
 *                    line_only, los, free_cell, crit_chance, fail_chance,
 *                    cooldown, max_per_turn, max_per_target,
 *                    effects, crit_effects)
 *     effects/crit_effects : chaîne "#" séparée :
 *       "effectId,diceMin,diceMax,special,zone,zoneSize,element#..."
 *
 * TODO : créer spell_system.sql avec les sorts officiels Dofus 1.29.
 */
public class SpellsData {

    private static final Logger logger = LoggerFactory.getLogger(SpellsData.class);

    private static final ConcurrentMap<Integer, SpellTemplate> templates = new ConcurrentHashMap<>();

    // ── Chargement ────────────────────────────────────────────────────────────

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            loadTemplates(conn);
            loadLevels(conn);
            logger.info("SpellsData : {} sorts chargés", templates.size());
        } catch(Exception e) {
            logger.error("SpellsData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadTemplates(Connection conn) throws Exception {
        templates.clear();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, sprite_id FROM spell_templates");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                int id = rs.getInt("id");
                templates.put(id, new SpellTemplate(
                    id,
                    rs.getString("name"),
                    rs.getInt("sprite_id"),
                    new ArrayList<SpellLevel>()
                ));
            }
        }
    }

    private static void loadLevels(Connection conn) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT spell_id, level, ap_cost, min_range, max_range, " +
                "line_only, los, free_cell, crit_chance, fail_chance, " +
                "cooldown, max_per_turn, max_per_target, effects, crit_effects " +
                "FROM spell_levels ORDER BY spell_id, level");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                SpellTemplate tpl = templates.get(rs.getInt("spell_id"));
                if(tpl == null) continue;

                SpellLevel lvl = new SpellLevel(
                    parseEffects(rs.getString("effects")),
                    parseEffects(rs.getString("crit_effects")),
                    rs.getInt("min_range"),
                    rs.getInt("max_range"),
                    rs.getBoolean("line_only"),
                    rs.getBoolean("los"),
                    rs.getBoolean("free_cell"),
                    rs.getInt("ap_cost"),
                    rs.getInt("crit_chance"),
                    rs.getInt("fail_chance"),
                    rs.getInt("cooldown"),
                    rs.getInt("max_per_turn"),
                    rs.getInt("max_per_target")
                );
                tpl.getLevels().add(lvl);
            }
        }
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    public static SpellTemplate get(int id)         { return templates.get(id); }
    public static SpellTemplate getTemplate(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, SpellTemplate> getAll() { return templates; }

    /**
     * Construit le paquet {@code SL} pour un personnage.
     * Format : {@code SL{spellId~level~position;...}}
     *
     * @param spellBook Map spellId → niveau investi (depuis Characters ou BDD)
     */
    public static String buildSLPacket(java.util.Map<Integer, Integer> spellBook) {
        if(spellBook == null || spellBook.isEmpty()) return "SL";
        StringBuilder sb = new StringBuilder("SL");
        boolean first = true;
        int position = 1;
        for(java.util.Map.Entry<Integer, Integer> entry : spellBook.entrySet()) {
            if(!first) sb.append(';');
            // Format : spellId~level~position~0~1
            sb.append(entry.getKey()).append('~')
              .append(entry.getValue()).append('~')
              .append(position++).append('~')
              .append("0~1"); // TODO : paramètres exacts
            first = false;
        }
        return sb.toString();
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static List<SpellEffect> parseEffects(String raw) {
        List<SpellEffect> list = new ArrayList<>();
        if(raw == null || raw.trim().isEmpty()) return list;
        for(String part : raw.split("#")) {
            String[] f = part.split(",", 7);
            if(f.length < 7) continue;
            try {
                list.add(new SpellEffect(
                    Integer.parseInt(f[0].trim()),
                    Integer.parseInt(f[1].trim()),
                    Integer.parseInt(f[2].trim()),
                    Integer.parseInt(f[3].trim()),
                    f[4].trim(),
                    Integer.parseInt(f[5].trim()),
                    Integer.parseInt(f[6].trim())
                ));
            } catch(NumberFormatException e) {
                logger.debug("SpellsData : effet malformé ignoré : {}", part);
            }
        }
        return list;
    }
}
