package org.dofus.objects.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Instance d'objet appartenant a un personnage.
 *
 * Positions inventaire :
 * -1 sac, 0 amulette, 1 arme, 2 anneau gauche, 3 ceinture,
 * 4 anneau droit, 5 bottes, 6 coiffe, 7 cape, 8 familier,
 * 9 a 14 Dofus, 15 bouclier.
 */
public class Item {

    private final long uid;
    private ItemTemplate template;
    private int quantity;
    private int position;
    private final List<ItemEffect> rolledEffects;

    public Item(long uid, ItemTemplate template, int quantity, int position,
                List<ItemEffect> rolledEffects) {
        this.uid = uid;
        this.template = template;
        this.quantity = quantity;
        this.position = position;
        this.rolledEffects = rolledEffects != null
            ? normalizeEffects(template, rolledEffects)
            : rollFromTemplate(template);
    }

    public static Item create(long uid, ItemTemplate template, int quantity, int position) {
        return new Item(uid, template, quantity, position, null);
    }

    public static Item createMax(long uid, ItemTemplate template, int quantity, int position) {
        return new Item(uid, template, quantity, position, maxFromTemplate(template));
    }

    public long getUid() { return uid; }
    public ItemTemplate getTemplate() { return template; }
    public void setTemplate(ItemTemplate template) { this.template = template; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }
    public int getPosition() { return position; }
    public void setPosition(int p) { this.position = p; }
    public List<ItemEffect> getRolledEffects() { return Collections.unmodifiableList(rolledEffects); }

    public boolean hasEffect(int effectId) {
        return getEffectParam3(effectId) != Integer.MIN_VALUE;
    }

    public int getEffectParam3(int effectId) {
        for(ItemEffect effect : rolledEffects) {
            if(effect.getEffectId() == effectId) return effect.getMax();
        }
        return Integer.MIN_VALUE;
    }

    public ItemEffect getEffect(int effectId) {
        for(ItemEffect effect : rolledEffects) {
            if(effect.getEffectId() == effectId) return effect;
        }
        return null;
    }

    public int getEffectValue(int effectId) {
        ItemEffect effect = getEffect(effectId);
        return effect != null ? effect.getValue() : Integer.MIN_VALUE;
    }

    public void replaceEffectParam3(int effectId, int value) {
        removeEffect(effectId);
        rolledEffects.add(ItemEffect.param3(effectId, value));
    }

    public void replaceEffect(ItemEffect effect) {
        if(effect == null) return;
        removeEffect(effect.getEffectId());
        rolledEffects.add(effect);
    }

    public void removeLivingEffects() {
        removeEffect(970);
        removeEffect(971);
        removeEffect(972);
        removeEffect(973);
        removeEffect(974);
    }

    public void removeEffect(int effectId) {
        rolledEffects.removeIf(effect -> effect.getEffectId() == effectId);
    }

    public boolean hasUnrolledEffects() {
        for(ItemEffect effect : rolledEffects) {
            if(effect.shouldRollInstance()) return true;
        }
        return false;
    }

    public void rerollEffectsFromTemplate() {
        rolledEffects.clear();
        rolledEffects.addAll(rollFromTemplate(template));
    }

    public boolean hasSameRolledEffects(Item other) {
        if(other == null) return false;
        List<ItemEffect> otherEffects = other.getRolledEffects();
        if(rolledEffects.size() != otherEffects.size()) return false;
        for(int i = 0; i < rolledEffects.size(); i++) {
            if(!rolledEffects.get(i).toDofusString().equals(otherEffects.get(i).toDofusString())) {
                return false;
            }
        }
        return true;
    }

    public boolean isEquipped() { return position >= 0; }

    public int getVisualTemplateId() {
        int livingVisual = getEffectParam3(970);
        return livingVisual != Integer.MIN_VALUE ? livingVisual : template.getId();
    }

    /**
     * Serialise l'item au format Dofus 1.29.
     * Format : guidHex~templateHex~quantityHex~positionHex~stats;
     * La position est vide quand l'objet est dans le sac.
     */
    public String toOLEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toHexString(uid)).append('~')
          .append(Integer.toHexString(template.getId())).append('~')
          .append(Integer.toHexString(quantity)).append('~');
        if(position >= 0) sb.append(Integer.toHexString(position));
        sb.append('~');

        if(!rolledEffects.isEmpty()) sb.append(',');
        boolean first = true;
        for(ItemEffect e : rolledEffects) {
            if(!first) sb.append(',');
            sb.append(e.toDofusString());
            first = false;
        }
        sb.append(';');
        return sb.toString();
    }

    private static List<ItemEffect> rollFromTemplate(ItemTemplate template) {
        List<ItemEffect> result = new ArrayList<>();
        if(template == null) return result;
        if(template.getTypeId() == 18) return createInitialPetEffects(template);
        for(ItemEffect e : template.getEffects()) result.add(e.rollInstance());
        return normalizeEffects(template, result);
    }

    private static List<ItemEffect> maxFromTemplate(ItemTemplate template) {
        List<ItemEffect> result = new ArrayList<ItemEffect>();
        if(template == null) return result;
        if(template.getTypeId() == 18) return createInitialPetEffects(template);
        for(ItemEffect effect : template.getEffects()) result.add(effect.maxInstance());
        return normalizeEffects(template, result);
    }

    private static List<ItemEffect> normalizeEffects(ItemTemplate template, List<ItemEffect> effects) {
        List<ItemEffect> result = normalizeEtherealDurability(effects);
        if(template != null && template.getTypeId() == 18) return normalizePetEffects(template, result);
        if(template == null || template.getTypeId() != 113) return result;

        ensureEffectParam3(result, 971, 0);
        ensureEffectParam3(result, 972, 1);
        ensureEffectParam3(result, 973, livingSupportedType(template.getId()));
        ensureEffectParam3(result, 974, 0);
        return result;
    }

    private static List<ItemEffect> createInitialPetEffects(ItemTemplate template) {
        List<ItemEffect> result = new ArrayList<ItemEffect>();
        int maxLife = petMaxLife(template);
        result.add(ItemEffect.petLife(maxLife, maxLife));
        result.add(ItemEffect.petBodyState(0));
        result.add(new ItemEffect(807, 0, 0, 0, "0d0+0"));
        return result;
    }

    private static List<ItemEffect> normalizePetEffects(ItemTemplate template, List<ItemEffect> effects) {
        List<ItemEffect> result = new ArrayList<ItemEffect>();
        boolean hasLife = false;
        int defaultMaxLife = petMaxLife(template);
        for(ItemEffect effect : effects) {
            if(effect.getEffectId() != 800) {
                result.add(effect);
                continue;
            }

            int maximum = effect.getMax() > 0 ? effect.getMax() : defaultMaxLife;
            int current = effect.getDice() > 0 ? effect.getDice() : effect.getValue();
            result.add(ItemEffect.petLife(current, maximum));
            hasLife = true;
        }
        if(!hasLife) result.add(ItemEffect.petLife(defaultMaxLife, defaultMaxLife));
        if(!hasEffect(result, 806)) result.add(ItemEffect.petBodyState(0));
        if(!hasEffect(result, 807)) result.add(new ItemEffect(807, 0, 0, 0, "0d0+0"));
        return result;
    }

    private static boolean hasEffect(List<ItemEffect> effects, int effectId) {
        for(ItemEffect effect : effects) {
            if(effect.getEffectId() == effectId) return true;
        }
        return false;
    }

    private static int petMaxLife(ItemTemplate template) {
        if(template != null) {
            for(ItemEffect effect : template.getEffects()) {
                if(effect.getEffectId() == 800) return Math.max(1, effect.getValue());
            }
        }
        return 10;
    }

    private static List<ItemEffect> normalizeEtherealDurability(List<ItemEffect> effects) {
        List<ItemEffect> result = new ArrayList<>();
        for(ItemEffect effect : effects) {
            if(effect.getEffectId() == 812) {
                int maximum = effect.getMax() > 0 ? effect.getMax() : effect.getValue();
                int current = effect.getMin() > 0 ? effect.getMin() : maximum;
                result.add(ItemEffect.etherealDurability(current, maximum));
            } else {
                result.add(effect);
            }
        }
        return result;
    }

    private static void ensureEffectParam3(List<ItemEffect> effects, int effectId, int value) {
        for(ItemEffect effect : effects) {
            if(effect.getEffectId() == effectId) return;
        }
        effects.add(ItemEffect.param3(effectId, value));
    }

    private static int livingSupportedType(int templateId) {
        switch(templateId) {
            case 9233: return 17;
            case 9234: return 16;
            case 9255: return 1;
            case 9256: return 9;
            default:   return 0;
        }
    }

    @Override
    public String toString() {
        return "Item{uid=" + uid + ", tpl=" + template.getId()
            + " '" + template.getName() + "', qty=" + quantity
            + ", pos=" + position + "}";
    }
}
