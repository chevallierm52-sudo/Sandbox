package org.dofus.objects.items;

/**
 * Un effet d'objet Dofus 1.29.
 *
 * Correspond à une ligne de la table {@code item_effects} ou à un champ inline dans {@code item_templates}.
 *
 * Format paquet : {@code effectId,dice,min,max,special}
 *   - effectId : identifiant de l'effet (ex : 111 = +Vitalité, 112 = +Sagesse, etc.)
 *   - dice      : nombre de dés (0 si valeur fixe)
 *   - min       : valeur minimale (ou valeur fixe si dice=0)
 *   - max       : valeur maximale (0 si valeur fixe)
 *   - special   : paramètre spécial (0 en général, peut être un templateId de sort, etc.)
 */
public class ItemEffect {

    // Constantes des effects IDs les plus courants
    public static final int VITALITY  = 111;
    public static final int WISDOM    = 112;
    public static final int STRENGTH  = 118;
    public static final int AGILITY   = 119;
    public static final int CHANCE    = 123;
    public static final int INTEL     = 126;
    public static final int PODS_BONUS = 158;
    public static final int INITIATIVE = 174;
    public static final int PROSPECTING = 176;
    public static final int AP_BONUS  = 1001;
    public static final int MP_BONUS  = 1003;

    private final int effectId;
    private final int dice;
    private final int min;
    private final int max;
    private final int special;

    public ItemEffect(int effectId, int dice, int min, int max, int special) {
        this.effectId = effectId;
        this.dice     = dice;
        this.min      = min;
        this.max      = max;
        this.special  = special;
    }

    /** Crée un effet fixe (dice=0, min=max=value). */
    public static ItemEffect fixed(int effectId, int value) {
        return new ItemEffect(effectId, 0, value, 0, 0);
    }

    /** Crée un effet aléatoire (min à max). */
    public static ItemEffect random(int effectId, int min, int max) {
        return new ItemEffect(effectId, 1, min, max, 0);
    }

    public int getEffectId() { return effectId; }
    public int getDice()     { return dice;     }
    public int getMin()      { return min;      }
    public int getMax()      { return max;      }
    public int getSpecial()  { return special;  }

    /** Valeur effective (aléatoire si dice > 0, fixe sinon). */
    public int roll() {
        if(dice > 0 && max > min) return min + (int)(Math.random() * (max - min + 1));
        return min;
    }
}
