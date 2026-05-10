package org.dofus.objects.items;

import java.util.Collections;
import java.util.List;

/**
 * Modèle d'objet Dofus 1.29 — chargé depuis la table {@code item_templates}.
 *
 * Champs principaux : id, nom, type, niveau, poids, prix, effets statiques.
 * Les effets sont une liste de {@link ItemEffect} (bonus fixe ou aléatoire).
 */
public class ItemTemplate {

    /** Types d'objets (valeur = typeId Dofus) */
    public enum ItemType {
        AMULETTE(1), ANNEAU(2), CEINTURE(3), BOTTE(4), CHAPEAU(5),
        CAPE(6), SACS(7), EQUIPEMENT(8), RESSOURCE(48),
        PARCHEMIN(69), CONSOMMABLE(74), PANOPLIE(66),
        WEAPON_SWORD(1), WEAPON_STAFF(2); // TODO : compléter les types d'armes

        public final int id;
        ItemType(int id) { this.id = id; }
    }

    private final int    id;
    private final String name;
    private final int    typeId;
    private final int    level;
    private final int    pods;   // poids en grammes
    private final long   price;  // prix en kamas (marchand PNJ)
    private final int    gfxId;  // sprite client
    private final List<ItemEffect> effects;

    public ItemTemplate(int id, String name, int typeId, int level,
                        int pods, long price, int gfxId, List<ItemEffect> effects) {
        this.id      = id;
        this.name    = name;
        this.typeId  = typeId;
        this.level   = level;
        this.pods    = pods;
        this.price   = price;
        this.gfxId   = gfxId;
        this.effects = effects != null ? effects : Collections.emptyList();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int    getId()      { return id;      }
    public String getName()    { return name;    }
    public int    getTypeId()  { return typeId;  }
    public int    getLevel()   { return level;   }
    public int    getPods()    { return pods;    }
    public long   getPrice()   { return price;   }
    public int    getGfxId()   { return gfxId;   }
    public List<ItemEffect> getEffects() { return effects; }

    /**
     * Génère la chaîne d'effets pour le paquet {@code OL} / {@code OI}.
     * Format Dofus 1.29 : {@code effectId,dice,min,max,special#effectId,...}
     */
    public String buildEffectsString() {
        if(effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(ItemEffect e : effects) {
            if(sb.length() > 0) sb.append('#');
            sb.append(e.getEffectId()).append(',')
              .append(e.getDice())    .append(',')
              .append(e.getMin())     .append(',')
              .append(e.getMax())     .append(',')
              .append(e.getSpecial());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ItemTemplate{id=" + id + ", name='" + name + "', type=" + typeId + ", lvl=" + level + "}";
    }
}
