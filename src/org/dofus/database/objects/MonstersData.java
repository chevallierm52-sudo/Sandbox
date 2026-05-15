package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.dofus.objects.monsters.MonsterTemplate.MonsterGrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chargement des monstres Dofus 1.29.
 *
 * Tables attendues :
 *   monster_templates (id, name, gfx_id, race, alignment)
 *   monster_grades    (monster_id, grade, level, life, ap, mp,
 *                      strength, agility, intel, wisdom, chance,
 *                      res_neutral, res_earth, res_fire, res_water, res_air, xp)
 *   monster_spawns    (map_id, monster_id, grade, cell_id, orientation, qty)
 *
 * TODO : créer le fichier monster_system.sql avec données de base.
 */
public class MonstersData {

    private static final Logger logger = LoggerFactory.getLogger(MonstersData.class);

    /** Cache global des templates : id → MonsterTemplate */
    private static final ConcurrentMap<Integer, MonsterTemplate> templates = new ConcurrentHashMap<>();

    private static final int GROUP_SIZE_MIN = 2;
    private static final int GROUP_SIZE_MAX = 5;
    private static final Random groupRng = new Random();

    // ── Chargement global ─────────────────────────────────────────────────────

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            loadTemplates(conn);
            loadGrades(conn);
            logger.info("MonstersData : {} templates chargés", templates.size());
        } catch(Exception e) {
            logger.error("MonstersData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static void loadTemplates(Connection conn) throws Exception {
        templates.clear();
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, gfx_id, race, alignment FROM monster_templates");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                int id = rs.getInt("id");
                templates.put(id, new MonsterTemplate(
                    id,
                    rs.getString("name"),
                    rs.getInt("gfx_id"),
                    rs.getInt("race"),
                    rs.getInt("alignment"),
                    new ArrayList<MonsterGrade>() // grades remplis ensuite
                ));
            }
        }
    }

    private static void loadGrades(Connection conn) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT monster_id, grade, level, life, ap, mp, " +
                "strength, agility, intel, wisdom, chance, " +
                "res_neutral, res_earth, res_fire, res_water, res_air, " +
                "xp, kamas_min, kamas_max " +
                "FROM monster_grades ORDER BY monster_id, grade");
            ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
                MonsterTemplate tpl = templates.get(rs.getInt("monster_id"));
                if(tpl == null) continue;
                MonsterGrade grade = new MonsterGrade(
                    rs.getInt("grade"),
                    rs.getInt("level"),
                    rs.getInt("life"),
                    rs.getInt("ap"),
                    rs.getInt("mp"),
                    rs.getInt("strength"),
                    rs.getInt("agility"),
                    rs.getInt("intel"),
                    rs.getInt("wisdom"),
                    rs.getInt("chance"),
                    rs.getInt("res_neutral"),
                    rs.getInt("res_earth"),
                    rs.getInt("res_fire"),
                    rs.getInt("res_water"),
                    rs.getInt("res_air"),
                    rs.getLong("xp"), 
                    rs.getInt("kamas_min"), 
                    rs.getInt("kamas_max")
                );
                // On ajoute directement dans la liste — grades est modifiable dans MonsterTemplate
                tpl.getGrades().add(grade);
            }
        }
    }

    // ── Spawn sur une carte ───────────────────────────────────────────────────

    /**
     * Charge et spawn tous les groupes de monstres définis pour une carte.
     * Appelé dans {@link MapTemplate} à l'initialisation ou au reload.
     *
     * @param map La carte cible
     */
    public static void spawnAll(MapTemplate map) {
        if(map == null) return;

        if(templates.isEmpty()) {
            logger.warn("MonstersData.spawnAll({}) ignoré : templates monstres non chargés", map.getId());
            return;
        }

        if(map.areMonsterGroupsSpawned()) return;

        Connection conn = null;
        try {
            conn = Connector.acquire();
            int count = spawnForMap(conn, map);
            map.setMonsterGroupsSpawned(true);
            logger.debug("MonstersData : {} groupe(s) monstre(s) préparé(s) sur map {}", count, map.getId());
        } catch(Exception e) {
            logger.error("MonstersData.spawnAll({}) failed: {}", map.getId(), e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static int spawnForMap(Connection conn, MapTemplate map) throws Exception {
        List<RawSpawnEntry> pool = new ArrayList<>();

        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT monster_id, grade, cell_id, orientation, qty " +
                "FROM monster_spawns WHERE map_id=? ORDER BY cell_id, orientation, monster_id, grade")) {
            ps.setInt(1, map.getId());
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    MonsterTemplate tpl = templates.get(rs.getInt("monster_id"));
                    if(tpl == null) continue;

                    int grade = rs.getInt("grade");
                    short cell = rs.getShort("cell_id");
                    int orientOrd = rs.getInt("orientation");
                    int qty = rs.getInt("qty");
                    if(qty <= 0) qty = 1;

                    EOrientation orient = EOrientation.valueOf(orientOrd);
                    for(int i = 0; i < qty; i++) {
                        pool.add(new RawSpawnEntry(tpl, grade, cell, orient));
                    }
                }
            }
        }

        if(pool.isEmpty()) return 0;

        /*
         * Chaque ligne SQL = un monstre individuel avec sa cellule d'ancrage.
         * On mélange le pool puis on découpe en groupes de GROUP_SIZE_MIN–GROUP_SIZE_MAX
         * membres (comportement officiel : 2–5 monstres par groupe en moyenne).
         */
        Collections.shuffle(pool, groupRng);

        int count = 0;
        int idx = 0;
        while(idx < pool.size()) {
            int remaining = pool.size() - idx;
            int maxSize = Math.min(GROUP_SIZE_MAX, remaining);
            int size = pickGroupSize(maxSize, groupRng);

            short groupCell = pool.get(idx).cell;
            EOrientation groupOrient = pool.get(idx).orient;

            List<MonsterGroup.Member> members = new ArrayList<>(size);
            for(int i = 0; i < size; i++) {
                RawSpawnEntry e = pool.get(idx + i);
                members.add(new MonsterGroup.Member(e.tpl, e.grade));
            }
            idx += size;

            Short naturalCell = map.findNearestValidMonsterCell(groupCell);
            if(naturalCell == null) {
                logger.warn("MonstersData : spawn ignoré sur map {} cellule {} : aucune cellule valide",
                    map.getId(), groupCell);
                continue;
            }

            short cell = naturalCell.shortValue();
            if(cell != groupCell) {
                logger.debug("MonstersData : cellule déplacée naturellement sur map {} : {} -> {}",
                    new Object[] { map.getId(), groupCell, cell });
            }

            MonsterGroup group = new MonsterGroup(map, cell, groupOrient, members);
            map.addMonsterGroup(group);
            count++;
            logger.debug("MonstersData : groupe {} spawné sur map {} cellule {} ({} monstres)",
                new Object[] { group.getId(), map.getId(), cell, group.getMembers().size() });
        }

        return count;
    }

    /**
     * Distribution officielle AncestraR (MobGroup constructor) du nombre de mobs par groupe.
     * Reproduit les probabilités exactes selon le maxSize de la zone.
     */
    private static int pickGroupSize(int maxSize, java.util.Random rng) {
        if (maxSize <= 1) return 1;
        int rand = rng.nextInt(100);
        switch (maxSize) {
            case 2: return rand < 50 ? 1 : 2;
            case 3: return rand < 33 ? 1 : (rand < 66 ? 2 : 3);
            case 4: // 22/26/26/26
                if (rand < 22) return 1;
                if (rand < 48) return 2;
                if (rand < 74) return 3;
                return 4;
            case 5: // 15/20/25/25/15
                if (rand < 15) return 1;
                if (rand < 35) return 2;
                if (rand < 60) return 3;
                if (rand < 85) return 4;
                return 5;
            case 6: // 10/15/20/20/20/15
                if (rand < 10) return 1;
                if (rand < 25) return 2;
                if (rand < 45) return 3;
                if (rand < 65) return 4;
                if (rand < 85) return 5;
                return 6;
            case 7: // 9/11/15/20/20/16/9
                if (rand < 9) return 1;
                if (rand < 20) return 2;
                if (rand < 35) return 3;
                if (rand < 55) return 4;
                if (rand < 75) return 5;
                if (rand < 91) return 6;
                return 7;
            default: // 8+ : 9/11/13/17/17/13/11/9
                if (rand < 9) return 1;
                if (rand < 20) return 2;
                if (rand < 33) return 3;
                if (rand < 50) return 4;
                if (rand < 67) return 5;
                if (rand < 80) return 6;
                if (rand < 91) return 7;
                return 8;
        }
    }

    private static final class RawSpawnEntry {
        final MonsterTemplate tpl;
        final int grade;
        final short cell;
        final EOrientation orient;

        RawSpawnEntry(MonsterTemplate tpl, int grade, short cell, EOrientation orient) {
            this.tpl = tpl;
            this.grade = grade;
            this.cell = cell;
            this.orient = orient != null ? orient : EOrientation.SOUTH;
        }
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    public static MonsterTemplate get(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, MonsterTemplate> getAll() { return templates; }
}
