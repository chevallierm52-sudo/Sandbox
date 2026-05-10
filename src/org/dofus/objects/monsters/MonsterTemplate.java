package org.dofus.objects.monsters;

import java.util.List;

/**
 * Template d'un monstre Dofus 1.29 — chargé depuis {@code monster_templates}.
 * Les statistiques par niveau sont dans {@link MonsterGrade}.
 */
public class MonsterTemplate {

    private final int    id;
    private final String name;
    private final int    gfxId;
    private final int    race;
    private final int    alignment;
    private final List<MonsterGrade> grades;

    public MonsterTemplate(int id, String name, int gfxId, int race, int alignment,
                           List<MonsterGrade> grades) {
        this.id        = id;
        this.name      = name;
        this.gfxId     = gfxId;
        this.race      = race;
        this.alignment = alignment;
        this.grades    = grades;
    }

    public int    getId()        { return id;        }
    public String getName()      { return name;      }
    public int    getGfxId()     { return gfxId;     }
    public int    getRace()      { return race;      }
    public int    getAlignment() { return alignment; }

    /** Grade 1-indexed. Retourne null si hors plage. */
    public MonsterGrade getGrade(int grade) {
        if(grade < 1 || grade > grades.size()) return null;
        return grades.get(grade - 1);
    }

    public List<MonsterGrade> getGrades()  { return grades; }
    public int                getGradeCount() { return grades.size(); }

    // ── Grade ─────────────────────────────────────────────────────────────────

    public static class MonsterGrade {
        private final int  grade;
        private final int  level;
        private final int  life;
        private final int  ap;
        private final int  mp;
        private final int  strength;
        private final int  agility;
        private final int  intel;
        private final int  wisdom;
        private final int  chance;
        private final int  neutral;
        private final int  earth;
        private final int  fire;
        private final int  water;
        private final int  air;
        private final long xpBase;
        private final int  kamasMin;
        private final int  kamasMax;

        public MonsterGrade(int grade, int level, int life, int ap, int mp,
                            int strength, int agility, int intel, int wisdom, int chance,
                            int neutral, int earth, int fire, int water, int air,
                            long xpBase, int kamasMin, int kamasMax) {
            this.grade    = grade;
            this.level    = level;
            this.life     = life;
            this.ap       = ap;
            this.mp       = mp;
            this.strength = strength;
            this.agility  = agility;
            this.intel    = intel;
            this.wisdom   = wisdom;
            this.chance   = chance;
            this.neutral  = neutral;
            this.earth    = earth;
            this.fire     = fire;
            this.water    = water;
            this.air      = air;
            this.xpBase   = xpBase;
            this.kamasMin = kamasMin;
            this.kamasMax = kamasMax;
        }

        public int  getGrade()    { return grade;    }
        public int  getLevel()    { return level;    }
        public int  getLife()     { return life;     }
        public int  getAp()       { return ap;       }
        public int  getMp()       { return mp;       }
        public int  getStrength() { return strength; }
        public int  getAgility()  { return agility;  }
        public int  getIntel()    { return intel;    }
        public int  getWisdom()   { return wisdom;   }
        public int  getChance()   { return chance;   }
        public int  getNeutral()  { return neutral;  }
        public int  getEarth()    { return earth;    }
        public int  getFire()     { return fire;     }
        public int  getWater()    { return water;    }
        public int  getAir()      { return air;      }
        public long getXpBase()   { return xpBase;   }
        /** Alias rétrocompat. */
        public long getXp()       { return xpBase;   }
        public int  getKamasMin() { return kamasMin; }
        public int  getKamasMax() { return kamasMax; }
    }
}
