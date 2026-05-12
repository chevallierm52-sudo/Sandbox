package org.dofus.objects.items;

import java.util.Collections;
import java.util.List;

/**
 * Modele d'objet Dofus 1.29 charge depuis item_template.
 */
public class ItemTemplate {

    public enum ItemType {
        AMULETTE(1), ARC(2), BAGUETTE(3), BATON(4), DAGUE(5),
        EPEE(6), MARTEAU(7), PELLE(8), ANNEAU(9), CEINTURE(10),
        BOTTE(11), CHAPEAU(16), CAPE(17), FAMILIER(18), HACHE(19),
        DOFUS(23), RESSOURCE(48), PANOPLIE(66), PARCHEMIN(69),
        CONSOMMABLE(74), BOUCLIER(82), CADEAU(89), OBJET_VIVANT(113);

        public final int id;
        ItemType(int id) { this.id = id; }
    }

    private final int id;
    private final String name;
    private final int typeId;
    private final int level;
    private final int pods;
    private final long price;
    private final int gfxId;
    private final String conditions;
    private final boolean twoHanded;
    private final List<ItemEffect> effects;

    public ItemTemplate(int id, String name, int typeId, int level,
                        int pods, long price, int gfxId, List<ItemEffect> effects) {
        this(id, name, typeId, level, pods, price, gfxId, effects, "", false);
    }

    public ItemTemplate(int id, String name, int typeId, int level,
                        int pods, long price, int gfxId, List<ItemEffect> effects,
                        String conditions, boolean twoHanded) {
        this.id = id;
        this.name = name;
        this.typeId = typeId;
        this.level = level;
        this.pods = pods;
        this.price = price;
        this.gfxId = gfxId;
        this.conditions = conditions != null ? conditions.trim() : "";
        this.twoHanded = twoHanded;
        this.effects = effects != null ? effects : Collections.emptyList();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getTypeId() { return typeId; }
    public int getLevel() { return level; }
    public int getPods() { return pods; }
    public long getPrice() { return price; }
    public int getGfxId() { return gfxId; }
    public String getConditions() { return conditions; }
    public boolean isTwoHanded() { return twoHanded; }
    public List<ItemEffect> getEffects() { return effects; }

    public String buildEffectsString() {
        if(effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(ItemEffect e : effects) {
            if(sb.length() > 0) sb.append(',');
            sb.append(e.toDofusString());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ItemTemplate{id=" + id + ", name='" + name + "', type=" + typeId + ", lvl=" + level + "}";
    }
}
