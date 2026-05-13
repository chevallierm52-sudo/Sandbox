package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.spells.KnownSpell;
import org.dofus.objects.spells.SpellTemplate;
import org.dofus.objects.spells.SpellTemplate.SpellEffect;
import org.dofus.objects.spells.SpellTemplate.SpellLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpellsData {

    private static final Logger logger = LoggerFactory.getLogger(SpellsData.class);
    private static final String HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    private static final ConcurrentMap<Integer, SpellTemplate> templates = new ConcurrentHashMap<>();
    private static final Map<Integer, List<BreedSpell>> fallbackBreedSpells = buildFallbackBreedSpells();
    private static volatile boolean spellTablesReady = false;

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureSpellTables(conn);
            loadTemplates(conn);
            loadLevels(conn);
            logger.info("SpellsData : {} sorts charges", templates.size());
        } catch(Exception e) {
            logger.error("SpellsData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadTemplates(Connection conn) throws Exception {
        templates.clear();
        try(PreparedStatement ps = conn.prepareStatement("SELECT id, name, sprite_id FROM spell_templates");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                int id = rs.getInt("id");
                templates.put(id, new SpellTemplate(id, rs.getString("name"), rs.getInt("sprite_id"), new ArrayList<SpellLevel>()));
            }
        }
    }

    private static void loadLevels(Connection conn) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT spell_id, level, ap_cost, min_range, max_range, "
              + "line_only, los, free_cell, crit_chance, fail_chance, "
              + "cooldown, max_per_turn, max_per_target, effects, crit_effects "
              + "FROM spell_levels ORDER BY spell_id, level");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                SpellTemplate tpl = templates.get(rs.getInt("spell_id"));
                if(tpl == null) continue;
                tpl.getLevels().add(new SpellLevel(
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
                ));
            }
        }
    }

    public static SpellTemplate get(int id)         { return templates.get(id); }
    public static SpellTemplate getTemplate(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, SpellTemplate> getAll() { return templates; }

    public static String buildSLPacket(Map<Integer, KnownSpell> spellBook) {
        if(spellBook == null || spellBook.isEmpty()) return "SL";
        StringBuilder sb = new StringBuilder("SL");
        for(KnownSpell spell : spellBook.values()) {
            sb.append(';')
              .append(spell.getSpellId()).append('~')
              .append(spell.getLevel()).append('~')
              .append(encodePosition(spell.getPosition()));
        }
        return sb.toString();
    }

    public static String buildSUPacket(KnownSpell spell) {
        return "SUK" + spell.getSpellId() + "~" + spell.getLevel() + "~" + encodePosition(spell.getPosition());
    }

    public static void loadCharacterSpells(Characters character) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureSpellTables(conn);
            LinkedHashMap<Integer, KnownSpell> loaded = loadKnownSpells(conn, character.getId());
            synchronizeUnlockedSpells(conn, character, loaded);
            character.getSpellBook().clear();
            for(KnownSpell spell : loaded.values()) character.learnSpell(spell);
        } catch(SQLException e) {
            logger.error("SpellsData.loadCharacterSpells({}) failed: {}", character.getName(), e.getMessage());
            loadFallbackSpellsOnly(character);
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static void saveKnownSpell(Characters character, KnownSpell spell) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            ensureSpellTables(conn);
            try(PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO character_spells(character_id, spell_id, spell_level, spell_pos) VALUES(?,?,?,?)")) {
                ps.setInt(1, character.getId());
                ps.setInt(2, spell.getSpellId());
                ps.setInt(3, spell.getLevel());
                ps.setInt(4, spell.getPosition());
                ps.executeUpdate();
            }
        } catch(SQLException e) {
            logger.error("SpellsData.saveKnownSpell({},{}) failed: {}", new Object[] { character.getName(), spell.getSpellId(), e.getMessage() });
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static boolean moveSpell(Characters character, int spellId, String rawPosition) {
        KnownSpell spell = character.getKnownSpell(spellId);
        if(spell == null) return false;
        spell.setPosition(decodePosition(rawPosition));
        saveKnownSpell(character, spell);
        return true;
    }

    private static List<SpellEffect> parseEffects(String raw) {
        List<SpellEffect> list = new ArrayList<>();
        if(raw == null || raw.trim().isEmpty() || "-1,0,0,0,0,0,0".equals(raw.trim())) return list;
        for(String part : raw.split("#")) {
            String[] f = part.split(",", 7);
            if(f.length < 6) continue;
            try {
                String diceStr = f.length >= 7 ? f[6].trim() : "";
                list.add(new SpellEffect(
                    Integer.parseInt(f[0].trim()),
                    Integer.parseInt(f[1].trim()),
                    Integer.parseInt(f[2].trim()),
                    Integer.parseInt(f[3].trim()),
                    f[4].trim(),
                    Integer.parseInt(f[5].trim()),
                    diceStr
                ));
            } catch(NumberFormatException e) {
                logger.debug("SpellsData : effet malforme ignore : {}", part);
            }
        }
        return list;
    }

    private static LinkedHashMap<Integer, KnownSpell> loadKnownSpells(Connection conn, int characterId) throws SQLException {
        LinkedHashMap<Integer, KnownSpell> spells = new LinkedHashMap<>();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT spell_id, spell_level, spell_pos FROM character_spells WHERE character_id=? ORDER BY spell_pos, spell_id")) {
            ps.setInt(1, characterId);
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    int spellId = rs.getInt("spell_id");
                    if(!templates.containsKey(spellId)) continue;
                    spells.put(spellId, new KnownSpell(spellId, rs.getInt("spell_level"), rs.getInt("spell_pos")));
                }
            }
        }
        return spells;
    }

    private static void synchronizeUnlockedSpells(Connection conn, Characters character, LinkedHashMap<Integer, KnownSpell> loaded) throws SQLException {
        List<BreedSpell> unlocked = loadBreedSpells(conn, character.getBreedId(), character.getExperience().getLevel());
        int position = nextFreePosition(loaded);
        for(BreedSpell spell : unlocked) {
            if(loaded.containsKey(spell.spellId) || !templates.containsKey(spell.spellId)) continue;
            KnownSpell known = new KnownSpell(spell.spellId, 1, position++);
            loaded.put(spell.spellId, known);
            try(PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO character_spells(character_id, spell_id, spell_level, spell_pos) VALUES(?,?,?,?)")) {
                ps.setInt(1, character.getId());
                ps.setInt(2, known.getSpellId());
                ps.setInt(3, known.getLevel());
                ps.setInt(4, known.getPosition());
                ps.executeUpdate();
            }
        }
    }

    private static List<BreedSpell> loadBreedSpells(Connection conn, int breedId, int level) {
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT spell_id, unlock_level FROM breed_spells WHERE breed_id=? AND unlock_level<=? ORDER BY unlock_level, spell_id")) {
            ps.setInt(1, breedId);
            ps.setInt(2, level);
            List<BreedSpell> spells = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) spells.add(new BreedSpell(rs.getInt("spell_id"), rs.getInt("unlock_level")));
            }
            if(!spells.isEmpty()) return spells;
        } catch(SQLException e) {
            logger.debug("breed_spells unavailable, using embedded Dofus 1.29 defaults: {}", e.getMessage());
        }
        List<BreedSpell> fallback = fallbackBreedSpells.get(breedId);
        if(fallback == null) return Collections.emptyList();
        List<BreedSpell> unlocked = new ArrayList<>();
        for(BreedSpell spell : fallback) if(spell.unlockLevel <= level) unlocked.add(spell);
        return unlocked;
    }

    private static void loadFallbackSpellsOnly(Characters character) {
        character.getSpellBook().clear();
        List<BreedSpell> fallback = fallbackBreedSpells.get(character.getBreedId() & 0xFF);
        if(fallback == null) return;
        int position = 1;
        int level = character.getExperience().getLevel();
        for(BreedSpell spell : fallback) {
            if(spell.unlockLevel <= level && templates.containsKey(spell.spellId)) {
                character.learnSpell(new KnownSpell(spell.spellId, 1, position++));
            }
        }
    }

    private static int nextFreePosition(Map<Integer, KnownSpell> loaded) {
        int max = 0;
        for(KnownSpell spell : loaded.values()) if(spell.getPosition() > max) max = spell.getPosition();
        return max + 1;
    }

    private static String encodePosition(int position) {
        if(position <= 0) return "";
        if(position >= HASH.length()) return String.valueOf(HASH.charAt(1));
        return String.valueOf(HASH.charAt(position));
    }

    private static int decodePosition(String raw) {
        if(raw == null || raw.length() == 0) return 0;
        int index = HASH.indexOf(raw.charAt(0));
        if(index >= 0) return index;
        try { return Integer.parseInt(raw); }
        catch(NumberFormatException e) { return 0; }
    }

    private static void ensureSpellTables(Connection conn) {
        if(spellTablesReady) return;
        try(PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS character_spells ("
              + "character_id INT NOT NULL, "
              + "spell_id INT NOT NULL, "
              + "spell_level TINYINT NOT NULL DEFAULT 1, "
              + "spell_pos TINYINT NOT NULL DEFAULT 0, "
              + "PRIMARY KEY(character_id, spell_id))")) {
            ps.executeUpdate();
            spellTablesReady = true;
        } catch(SQLException e) {
            logger.debug("SpellsData.ensureSpellTables ignored: {}", e.getMessage());
        }
    }

    private static Map<Integer, List<BreedSpell>> buildFallbackBreedSpells() {
        int[][] rows = new int[][] {
            {1,3,1},{1,6,1},{1,17,1},{1,4,3},{1,5,6},{1,1,9},{1,10,13},{1,2,17},{1,7,21},{1,9,26},{1,14,31},{1,12,36},{1,18,42},{1,13,48},{1,15,54},{1,16,60},{1,8,70},{1,20,80},{1,11,90},{1,19,100},
            {2,34,1},{2,21,1},{2,23,1},{2,22,3},{2,24,6},{2,25,9},{2,26,13},{2,27,17},{2,28,21},{2,29,26},{2,30,31},{2,31,36},{2,32,42},{2,33,48},{2,35,54},{2,36,60},{2,37,70},{2,38,80},{2,39,90},{2,40,100},
            {3,51,1},{3,43,1},{3,41,1},{3,42,3},{3,44,6},{3,45,9},{3,46,13},{3,47,17},{3,48,21},{3,49,26},{3,50,31},{3,52,36},{3,53,42},{3,54,48},{3,55,54},{3,56,60},{3,57,70},{3,58,80},{3,59,90},{3,60,100},
            {4,61,1},{4,72,1},{4,65,1},{4,62,3},{4,63,6},{4,64,9},{4,66,13},{4,67,17},{4,68,21},{4,69,26},{4,70,31},{4,71,36},{4,73,42},{4,74,48},{4,75,54},{4,76,60},{4,77,70},{4,78,80},{4,79,90},{4,80,100},
            {5,82,1},{5,81,1},{5,83,1},{5,84,3},{5,85,6},{5,86,9},{5,87,13},{5,88,17},{5,89,21},{5,90,26},{5,91,31},{5,92,36},{5,93,42},{5,94,48},{5,95,54},{5,96,60},{5,97,70},{5,98,80},{5,99,90},{5,100,100},
            {6,102,1},{6,103,1},{6,105,1},{6,101,3},{6,104,6},{6,106,9},{6,107,13},{6,108,17},{6,109,21},{6,110,26},{6,111,31},{6,112,36},{6,113,42},{6,114,48},{6,115,54},{6,116,60},{6,117,70},{6,118,80},{6,119,90},{6,120,100},
            {7,125,1},{7,128,1},{7,121,1},{7,122,3},{7,123,6},{7,124,9},{7,126,13},{7,127,17},{7,129,21},{7,130,26},{7,131,31},{7,132,36},{7,133,42},{7,134,48},{7,135,54},{7,136,60},{7,137,70},{7,138,80},{7,139,90},{7,140,100},
            {8,143,1},{8,141,1},{8,142,1},{8,144,3},{8,145,6},{8,146,9},{8,147,13},{8,148,17},{8,149,21},{8,150,26},{8,151,31},{8,152,36},{8,153,42},{8,154,48},{8,155,54},{8,156,60},{8,157,70},{8,158,80},{8,159,90},{8,160,100},
            {9,161,1},{9,169,1},{9,164,1},{9,162,3},{9,163,6},{9,165,9},{9,166,13},{9,167,17},{9,168,21},{9,170,26},{9,171,31},{9,172,36},{9,173,42},{9,174,48},{9,175,54},{9,176,60},{9,177,70},{9,178,80},{9,179,90},{9,180,100},
            {10,183,1},{10,200,1},{10,193,1},{10,181,3},{10,182,6},{10,184,9},{10,185,13},{10,186,17},{10,187,21},{10,188,26},{10,189,31},{10,190,36},{10,191,42},{10,192,48},{10,194,54},{10,195,60},{10,196,70},{10,197,80},{10,198,90},{10,199,100},
            {11,432,1},{11,431,1},{11,434,1},{11,433,3},{11,435,6},{11,436,9},{11,437,13},{11,438,17},{11,439,21},{11,440,26},{11,441,31},{11,442,36},{11,443,42},{11,444,48},{11,445,54},{11,446,60},{11,447,70},{11,448,80},{11,449,90},{11,450,100},
            {12,686,1},{12,692,1},{12,687,1},{12,688,3},{12,689,6},{12,690,9},{12,691,13},{12,693,17},{12,694,21},{12,695,26},{12,696,31},{12,697,36},{12,698,42},{12,699,48},{12,700,54},{12,701,60},{12,702,70},{12,703,80},{12,704,90},{12,705,100}
        };
        Map<Integer, List<BreedSpell>> map = new HashMap<>();
        for(int[] row : rows) {
            List<BreedSpell> list = map.get(row[0]);
            if(list == null) {
                list = new ArrayList<>();
                map.put(row[0], list);
            }
            list.add(new BreedSpell(row[1], row[2]));
        }
        return map;
    }

    private static class BreedSpell {
        final int spellId;
        final int unlockLevel;

        BreedSpell(int spellId, int unlockLevel) {
            this.spellId = spellId;
            this.unlockLevel = unlockLevel;
        }
    }
}
