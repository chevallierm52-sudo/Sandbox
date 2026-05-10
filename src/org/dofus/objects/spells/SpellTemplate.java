package org.dofus.objects.spells;

import java.util.List;

/**
 * Template d'un sort Dofus 1.29 — chargé depuis la table {@code spell_templates}.
 *
 * Un sort a plusieurs niveaux ({@link SpellLevel}, 1-6).
 * Le personnage dispose d'un niveau de sort (points de sort investis) qui détermine
 * quel {@link SpellLevel} est utilisé.
 *
 * Paquet {@code SL} : liste des sorts du personnage
 *   Format : {@code SL{spellId~spellLevel~position;...}}
 */
public class SpellTemplate {

    private final int    id;
    private final String name;
    private final int    spritId;     // sprite du sort dans le grimoire
    private final List<SpellLevel> levels;

    public SpellTemplate(int id, String name, int spritId, List<SpellLevel> levels) {
        this.id      = id;
        this.name    = name;
        this.spritId = spritId;
        this.levels  = levels;
    }

    public int    getId()      { return id;      }
    public String getName()    { return name;    }
    public int    getSpritId() { return spritId; }

    /** Retourne le niveau de sort correspondant (1-indexed). Null si hors plage. */
    public SpellLevel getLevel(int level) {
        if(level < 1 || level > levels.size()) return null;
        return levels.get(level - 1);
    }

    public List<SpellLevel> getLevels() { return levels; }

    // ── Niveau de sort ────────────────────────────────────────────────────────

    /**
     * Statistiques d'un niveau de sort (effets, portée, coûts, délais).
     */
    public static class SpellLevel {
        // Effets normaux et critiques
        private final List<SpellEffect> effects;
        private final List<SpellEffect> critEffects;

        private final int minRange;
        private final int maxRange;
        private final boolean lineOnly;
        private final boolean lineOfSight;
        private final boolean freeCell;    // peut cibler cellule vide

        private final int apCost;
        private final int criticalChance; // 0 = pas de critique
        private final int failureChance;  // chance d'échec critique

        private final int cooldown;       // rechargement en tours (0 = pas de recharge)
        private final int maxPerTurn;     // 0 = illimité
        private final int maxPerTarget;   // 0 = illimité

        public SpellLevel(List<SpellEffect> effects, List<SpellEffect> critEffects,
                          int minRange, int maxRange, boolean lineOnly, boolean lineOfSight, boolean freeCell,
                          int apCost, int criticalChance, int failureChance,
                          int cooldown, int maxPerTurn, int maxPerTarget) {
            this.effects        = effects;
            this.critEffects    = critEffects;
            this.minRange       = minRange;
            this.maxRange       = maxRange;
            this.lineOnly       = lineOnly;
            this.lineOfSight    = lineOfSight;
            this.freeCell       = freeCell;
            this.apCost         = apCost;
            this.criticalChance = criticalChance;
            this.failureChance  = failureChance;
            this.cooldown       = cooldown;
            this.maxPerTurn     = maxPerTurn;
            this.maxPerTarget   = maxPerTarget;
        }

        public List<SpellEffect> getEffects()      { return effects;        }
        public List<SpellEffect> getCritEffects()  { return critEffects;    }
        public int  getMinRange()       { return minRange;       }
        public int  getMaxRange()       { return maxRange;       }
        public boolean isLineOnly()     { return lineOnly;       }
        public boolean needsLineOfSight(){ return lineOfSight;   }
        public boolean isFreeCell()     { return freeCell;       }
        public int  getApCost()         { return apCost;         }
        public int  getCriticalChance() { return criticalChance; }
        public int  getFailureChance()  { return failureChance;  }
        public int  getCooldown()       { return cooldown;       }
        public int  getMaxPerTurn()     { return maxPerTurn;     }
        public int  getMaxPerTarget()   { return maxPerTarget;   }
    }

    // ── Effet de sort ─────────────────────────────────────────────────────────

    /**
     * Effet individuel d'un sort (dégâts, soin, buff, débuff, téléport...).
     *
     * effectId  : type d'effet (95 = dégâts feu, 91 = soin, 111 = +vitalité temporaire...)
     * zone      : lettre de zone (P=point, C=croix, X=croix diagonale, O=cercle...)
     * zoneSize  : taille de la zone
     * element   : 0=neutre, 1=terre, 2=feu, 3=eau, 4=air
     */
    public static class SpellEffect {
        private final int    effectId;
        private final int    diceMin;
        private final int    diceMax;
        private final int    special; // paramètre additionnel (durée buff, etc.)
        private final String zone;
        private final int    zoneSize;
        private final int    element;

        public SpellEffect(int effectId, int diceMin, int diceMax, int special,
                           String zone, int zoneSize, int element) {
            this.effectId = effectId;
            this.diceMin  = diceMin;
            this.diceMax  = diceMax;
            this.special  = special;
            this.zone     = zone;
            this.zoneSize = zoneSize;
            this.element  = element;
        }

        public int    getEffectId() { return effectId; }
        public int    getDiceMin()  { return diceMin;  }
        public int    getDiceMax()  { return diceMax;  }
        public int    getSpecial()  { return special;  }
        public String getZone()     { return zone;     }
        public int    getZoneSize() { return zoneSize; }
        public int    getElement()  { return element;  }

        /** Valeur rollée de l'effet (entre diceMin et diceMax). */
        public int roll() {
            if(diceMax <= diceMin) return diceMin;
            return diceMin + (int)(Math.random() * (diceMax - diceMin + 1));
        }
    }
}
