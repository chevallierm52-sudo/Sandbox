package org.dofus.objects.monsters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.actors.IActor;
import org.dofus.objects.maps.MapTemplate;

/**
 * Groupe de monstres sur une carte.
 *
 * Un groupe contient 1 à 8 monstres de templates et grades variés.
 * Format GM (composition) : {@code cell;dir;0;groupId;monsterId~grade,...;0;-1;0;0;0;|}
 *
 * Cycle de vie :
 *   1. Spawn via MonstersData.spawnAll()
 *   2. Retiré de la map au début d'un combat
 *   3. Réapparaît après délai via MapRespawnService
 */
public class MonsterGroup implements IActor {

    private static final AtomicInteger GROUP_ID_GEN = new AtomicInteger(200_000);

    private final int          groupId;
    private       MapTemplate  currentMap;
    private       short        currentCell;
    private       EOrientation orientation;

    // ── Membre ────────────────────────────────────────────────────────────────

    /** Représente un monstre dans le groupe (template + grade + PV courants). */
    public static class MonsterEntry {
        private final MonsterTemplate template;
        private final int             grade;
        private       int             currentLife;

        public MonsterEntry(MonsterTemplate template, int grade) {
            this.template    = template;
            this.grade       = grade;
            MonsterTemplate.MonsterGrade g = template.getGrade(grade);
            this.currentLife = (g != null) ? g.getLife() : 100;
        }

        public MonsterTemplate getTemplate()    { return template;    }
        public int             getGrade()       { return grade;       }
        public int             getCurrentLife() { return currentLife; }
        public void            setCurrentLife(int v) { this.currentLife = v; }

        /** Composition pour le paquet GM : {@code monsterId~grade} */
        public String toGMPart() { return template.getId() + "~" + grade; }
    }

    /** Alias pour compatibilité avec l'ancien nom {@code Member}. */
    public static class Member extends MonsterEntry {
        public Member(MonsterTemplate template, int grade) { super(template, grade); }
    }

    private final List<MonsterEntry> members = new ArrayList<>();

    // ── Constructeurs ─────────────────────────────────────────────────────────

    /** Constructeur principal (spawn depuis MonstersData). */
    public MonsterGroup(MapTemplate map, short cell, EOrientation orientation,
                        List<? extends MonsterEntry> members) {
        this.groupId     = GROUP_ID_GEN.getAndIncrement();
        this.currentMap  = map;
        this.currentCell = cell;
        this.orientation = (orientation != null) ? orientation : EOrientation.SOUTH;
        this.members.addAll(members);
    }

    /**
     * Constructeur léger pour MapRespawnService (sans map, pas encore ajouté).
     * La map est assignée quand le groupe est ajouté à la carte.
     */
    public MonsterGroup(int id, short cell, EOrientation orientation) {
        this.groupId     = id;
        this.currentCell = cell;
        this.orientation = (orientation != null) ? orientation : EOrientation.SOUTH;
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    public void addMember(MonsterTemplate template, int grade) {
        members.add(new MonsterEntry(template, grade));
    }

    public List<MonsterEntry> getMembers()     { return Collections.unmodifiableList(members); }
    public int                getMemberCount() { return members.size(); }

    public boolean isDead() {
        for(MonsterEntry m : members) if(m.getCurrentLife() > 0) return false;
        return true;
    }

    /** XP totale du groupe. */
    public long getTotalXp() {
        long total = 0;
        for(MonsterEntry m : members) {
            MonsterTemplate.MonsterGrade g = m.getTemplate().getGrade(m.getGrade());
            if(g != null) total += g.getXpBase();
        }
        return total;
    }

    // ── IActor ────────────────────────────────────────────────────────────────

    @Override public int getId()       { return groupId;     }
    @Override public int getActorId()  { return groupId;     }
    @Override public int getActorType(){ return 3;           } // 3 = groupe monstre

    @Override
    public EOrientation getOrientation() { return orientation; }

    @Override
    public MapTemplate getMapId()    { return currentMap;   }

    @Override
    public int getCellId()           { return currentCell;  }

    // Accesseurs commodes
    public MapTemplate  getCurrentMap()         { return currentMap;  }
    public void         setCurrentMap(MapTemplate m) { this.currentMap = m; }
    public short        getCell()               { return currentCell; }
    public short        getCurrentCell()        { return currentCell; }
    public EOrientation getCurrentOrientation() { return orientation; }

    // ── Sérialisation GM ──────────────────────────────────────────────────────

    /** Entrée GM complète : {@code cell;dir;0;groupId;composition;0;-1;0;0;0;|} */
    public String toGMEntry() {
        StringBuilder comp = new StringBuilder();
        for(MonsterEntry m : members) {
            if(comp.length() > 0) comp.append(',');
            comp.append(m.toGMPart());
        }
        return currentCell         + ";"
             + orientation.ordinal() + ";"
             + "0;"
             + groupId             + ";"
             + comp                + ";"
             + "0;-1;0;0;0;|";
    }

    /** Paquet de suppression : {@code GM|-groupId} */
    public String toGMRemove() { return "GM|-" + groupId; }
}
