package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
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
        int count = 0;
        try(PreparedStatement ps = conn.prepareStatement(
                "SELECT monster_id, grade, cell_id, orientation, qty " +
                "FROM monster_spawns WHERE map_id=?")) {
            ps.setInt(1, map.getId());
            try(ResultSet rs = ps.executeQuery()) {
                // Regroupe par cellule — TODO : groupes de plusieurs monstres
                while(rs.next()) {
                    MonsterTemplate tpl   = templates.get(rs.getInt("monster_id"));
                    if(tpl == null) continue;
                    int   grade       = rs.getInt("grade");
                    short cell        = rs.getShort("cell_id");
                    int   orientOrd   = rs.getInt("orientation");
                    int   qty         = rs.getInt("qty");

                    EOrientation orient = EOrientation.valueOf(orientOrd);

                    List<MonsterGroup.Member> members = new ArrayList<>();
                    for(int i = 0; i < qty; i++) {
                        members.add(new MonsterGroup.Member(tpl, grade));
                    }
                    MonsterGroup group = new MonsterGroup(map, cell, orient, members);
                    map.addMonsterGroup(group);
                    count++;
                    logger.debug("MonstersData : groupe {} spawné sur map {} cellule {}",
                        new Object[] { group.getId(), map.getId(), cell});
                }
            }
        }
        return count;
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    public static MonsterTemplate get(int id) { return templates.get(id); }
    public static ConcurrentMap<Integer, MonsterTemplate> getAll() { return templates; }
}
