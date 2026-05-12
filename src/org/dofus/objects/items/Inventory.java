package org.dofus.objects.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.dofus.objects.actors.Characters;
import org.dofus.constants.EConstants;
import org.dofus.objects.characters.Statistic;

/**
 * Inventaire d'un personnage.
 *
 * Positions Dofus 1.29 officielles :
 * -1 sac, 0 amulette, 1 arme, 2 anneau gauche, 3 ceinture,
 * 4 anneau droit, 5 bottes, 6 coiffe, 7 cape, 8 familier,
 * 9-14 Dofus, 15 bouclier.
 */
public class Inventory {

    private static final AtomicLong UID_GEN = new AtomicLong(1_000_000L);
    private final ConcurrentMap<Long, Item> items = new ConcurrentHashMap<>();
    private final List<Item> lastAutoUnequipped = Collections.synchronizedList(new ArrayList<Item>());

    public Item getByUid(long uid) {
        return items.get(uid);
    }

    public Collection<Item> getAll() {
        return items.values();
    }

    public List<Item> getBag() {
        List<Item> bag = new ArrayList<>();
        for(Item i : items.values()) if(!i.isEquipped()) bag.add(i);
        return bag;
    }

    public Item getEquipped(int position) {
        for(Item i : items.values()) if(i.getPosition() == position) return i;
        return null;
    }

    public Item addItem(ItemTemplate template, int quantity) {
        if(quantity <= 0) quantity = 1;
        if(isStackable(template)) {
            for(Item existing : items.values()) {
                if(existing.getTemplate().getId() == template.getId() && !existing.isEquipped()) {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    return existing;
                }
            }
        }
        long uid = nextUid();
        Item item = Item.create(uid, template, quantity, -1);
        items.put(uid, item);
        return item;
    }

    public static long nextUid() {
        return UID_GEN.getAndIncrement();
    }

    /**
     * Ajoute une instance deja existante dans le sac, en conservant ses jets.
     * Si l'objet est empilable et qu'une pile identique existe deja, on fusionne
     * dans la pile existante et l'instance passee en parametre reste hors inventaire.
     */
    public Item addExisting(Item item) {
        if(item == null) return null;
        item.setPosition(-1);

        for(Item existing : items.values()) {
            if(canMerge(existing, item)) {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                return existing;
            }
        }

        items.put(item.getUid(), item);
        return item;
    }

    private boolean canMerge(Item existing, Item incoming) {
        if(existing == null || incoming == null) return false;
        if(existing == incoming) return false;
        if(existing.isEquipped()) return false;
        if(!isStackable(incoming.getTemplate())) return false;
        if(existing.getTemplate().getId() != incoming.getTemplate().getId()) return false;
        return existing.hasSameRolledEffects(incoming);
    }

    /**
     * Accessoires visuels envoyes dans GM/Oa.
     *
     * Le client 1.29 ne lit pas ce champ comme les slots d'inventaire.
     * Dans CharactersManager.setSpriteAccessories/onAccessories, il fait un
     * split(",") puis commence volontairement a l'index 1. L'ordre des
     * layers graphiques garde donc un decalage volontaire :
     *   index 1 = coiffe
     *   index 2 = cape
     *   index 3 = familier
     *   index 4 = arme
     *   index 5 = bouclier
     *
     * En roleplay 1.29, on garde le slot arme vide dans les accessoires GM/Oa.
     * L'objet reste equipe en inventaire, sauvegarde en BDD, et pourra servir
     * aux stats/combat, mais on ne force pas son sprite sur le personnage.
     */
    public String buildAccessories() {
        return buildAccessories(false);
    }

    /**
     * @param includeWeapon true uniquement si une phase future veut afficher
     *                      l'arme dans un contexte specifique, par exemple combat.
     */
    public String buildAccessories(boolean includeWeapon) {
        StringBuilder sb = new StringBuilder();
        appendVisualAccessory(sb, 6, true);             // coiffe
        appendVisualAccessory(sb, 7, true);             // cape
        appendVisualAccessory(sb, 8, true);             // familier
        appendVisualAccessory(sb, 1, includeWeapon);    // arme, masquee en RP
        appendVisualAccessory(sb, 15, true);            // bouclier
        return sb.toString();
    }

    private void appendVisualAccessory(StringBuilder sb, int position, boolean visible) {
        sb.append(',');
        if(!visible) return;

        Item equipped = getEquipped(position);
        if(equipped != null) {
            sb.append(Integer.toHexString(equipped.getVisualTemplateId()).toUpperCase());
            int realType = equipped.getEffectParam3(973);
            int skin = equipped.getEffectParam3(972);
            if(realType != Integer.MIN_VALUE && skin != Integer.MIN_VALUE) {
                sb.append('~').append(realType).append('~').append(skin);
            }
        }
    }

    public static String buildOaPacket(org.dofus.objects.actors.Characters character) {
        if(character == null || character.getInventory() == null) return null;
        return "Oa" + character.getId() + "|" + character.getInventory().buildAccessories();
    }

    public boolean removeItem(long uid, int quantity) {
        Item item = items.get(uid);
        if(item == null || quantity <= 0) return false;
        int newQty = item.getQuantity() - quantity;
        if(newQty <= 0) {
            items.remove(uid);
        } else {
            item.setQuantity(newQty);
        }
        return true;
    }

    /**
     * Equipe automatiquement l'objet sur son slot par defaut.
     * Retourne l'objet deplace vers le sac si un slot etait occupe.
     */
    public Item equip(long uid) {
        return equip(null, uid);
    }

    public Item equip(Characters character, long uid) {
        Item item = items.get(uid);
        if(item == null) return null;
        int slot = defaultSlotFor(item);
        if(slot < 0) return null;
        return move(character, uid, slot);
    }

    /**
     * Deplace un objet vers une position client officielle.
     * requestedPosition = -1 remet l'objet dans le sac.
     */
    public Item move(long uid, int requestedPosition) {
        return move(null, uid, requestedPosition);
    }

    public Item move(Characters character, long uid, int requestedPosition) {
        lastAutoUnequipped.clear();

        Item item = items.get(uid);
        if(item == null) return null;

        if(requestedPosition < 0) {
            item.setPosition(-1);
            return null;
        }

        if(getEquipFailureMessage(character, item, requestedPosition) != null) return null;

        handleTwoHandedConflicts(item, requestedPosition);

        Item displaced = getEquipped(requestedPosition);
        if(displaced != null && displaced != item) displaced.setPosition(-1);

        item.setPosition(requestedPosition);
        return displaced == item ? null : displaced;
    }

    public List<Item> consumeAutoUnequipped() {
        if(lastAutoUnequipped.isEmpty()) return Collections.emptyList();
        List<Item> copy = new ArrayList<Item>(lastAutoUnequipped);
        lastAutoUnequipped.clear();
        return copy;
    }

    public String getEquipFailureMessage(Characters character, Item item, int requestedPosition) {
        if(item == null) return "Im11";
        if(requestedPosition < 0) return null;
        if(item.getQuantity() != 1) return "Im11";
        if(!isValidSlotFor(item, requestedPosition)) return "Im11";

        if(character != null && character.getExperience() != null
                && character.getExperience().getLevel() < item.getTemplate().getLevel()) {
            return "Im13"; // Tu n'as pas le niveau necessaire.
        }

        if(isDofusSlot(requestedPosition) && hasSameTemplateEquipped(item, 9, 14)) {
            return "Im11"; // Conditions non satisfaites / objet unique.
        }

        if(!matchesBasicConditions(character, item.getTemplate().getConditions())) {
            return "Im11";
        }

        return null;
    }

    private void handleTwoHandedConflicts(Item item, int requestedPosition) {
        if(item == null) return;

        if(requestedPosition == 15) {
            Item weapon = getEquipped(1);
            if(weapon != null && weapon.getTemplate().isTwoHanded()) {
                weapon.setPosition(-1);
                lastAutoUnequipped.add(weapon);
            }
        } else if(requestedPosition == 1 && item.getTemplate().isTwoHanded()) {
            Item shield = getEquipped(15);
            if(shield != null) {
                shield.setPosition(-1);
                lastAutoUnequipped.add(shield);
            }
        }
    }

    private boolean hasSameTemplateEquipped(Item item, int fromSlot, int toSlot) {
        for(int slot = fromSlot; slot <= toSlot; slot++) {
            Item equipped = getEquipped(slot);
            if(equipped != null && equipped != item
                    && equipped.getTemplate().getId() == item.getTemplate().getId()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDofusSlot(int position) {
        return position >= 9 && position <= 14;
    }

    private static boolean matchesBasicConditions(Characters character, String rawConditions) {
        if(rawConditions == null || rawConditions.trim().isEmpty()) return true;
        if(character == null) return true;

        String normalized = rawConditions.replace(" ", "")
                .replace("(", "")
                .replace(")", "");
        String[] andGroups = normalized.split("[&,]");
        for(String group : andGroups) {
            if(group.isEmpty()) continue;
            boolean groupMatches = false;
            String[] orConditions = group.split("\\|");
            for(String condition : orConditions) {
                if(condition.isEmpty()) continue;
                if(matchesCondition(character, condition)) {
                    groupMatches = true;
                    break;
                }
            }
            if(!groupMatches) return false;
        }
        return true;
    }

    private static boolean matchesCondition(Characters character, String condition) {
        String op = null;
        for(String candidate : new String[] {">=", "<=", "!=", "=", ">", "<"}) {
            int idx = condition.indexOf(candidate);
            if(idx > 0) { op = candidate; break; }
        }
        if(op == null) return true;

        String[] split = condition.split(java.util.regex.Pattern.quote(op), 2);
        if(split.length != 2) return true;
        String key = split[0];
        int expected;
        try { expected = Integer.parseInt(split[1]); }
        catch(NumberFormatException e) { return true; }

        int actual = conditionValue(character, key);
        if(actual == Integer.MIN_VALUE) return true;

        if(">=".equals(op)) return actual >= expected;
        if("<=".equals(op)) return actual <= expected;
        if("!=".equals(op)) return actual != expected;
        if("=".equals(op)) return actual == expected;
        if(">".equals(op)) return actual > expected;
        if("<".equals(op)) return actual < expected;
        return true;
    }

    private static int conditionValue(Characters character, String key) {
        if("PL".equalsIgnoreCase(key) || "level".equalsIgnoreCase(key)) {
            return character.getExperience() != null ? character.getExperience().getLevel() : 1;
        }
        if("PG".equalsIgnoreCase(key) || "breed".equalsIgnoreCase(key) || "class".equalsIgnoreCase(key)) return character.getBreedId();
        if("PA".equals(key) || "AP".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_AP.getInt());
        }
        if("PM".equals(key) || "MP".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_MP.getInt());
        }
        if("PS".equalsIgnoreCase(key) || "sex".equalsIgnoreCase(key)) return character.getGender();
        if("Ps".equals(key) || "align".equalsIgnoreCase(key)) return character.getAlignmentType();
        if("Pa".equals(key)) return character.getAlignment() != null ? character.getAlignment().getLevel() : 0;
        if("PAG".equalsIgnoreCase(key) || "grade".equalsIgnoreCase(key)) return character.getAlignmentGrade();
        if("PK".equalsIgnoreCase(key) || "kamas".equalsIgnoreCase(key)) return (int) Math.min(Integer.MAX_VALUE, character.getKamas());

        if("CS".equalsIgnoreCase(key) || "strength".equalsIgnoreCase(key) || "force".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_STRENGTH.getInt());
        }
        if("CV".equalsIgnoreCase(key) || "vitality".equalsIgnoreCase(key) || "vita".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_VITALITY.getInt());
        }
        if("CW".equalsIgnoreCase(key) || "wisdom".equalsIgnoreCase(key) || "sagesse".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_WISDOM.getInt());
        }
        if("CI".equalsIgnoreCase(key) || "intelligence".equalsIgnoreCase(key) || "intel".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_INTELLIGENCE.getInt());
        }
        if("CC".equalsIgnoreCase(key) || "chance".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_CHANCE.getInt());
        }
        if("CA".equalsIgnoreCase(key) || "agility".equalsIgnoreCase(key) || "agilite".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_AGILITY.getInt());
        }
        if("CP".equalsIgnoreCase(key) || "prospecting".equalsIgnoreCase(key) || "prospection".equalsIgnoreCase(key)) {
            return Statistic.totalWithEquipment(character, EConstants.ADD_PROSPECTION.getInt());
        }
        return Integer.MIN_VALUE;
    }

    public boolean unequip(long uid) {
        Item item = items.get(uid);
        if(item == null || !item.isEquipped()) return false;
        item.setPosition(-1);
        return true;
    }

    public int getUsedPods() {
        int total = 0;
        for(Item i : items.values()) total += i.getTemplate().getPods() * i.getQuantity();
        return total;
    }

    public String buildOLPacket() {
        if(items.isEmpty()) return "OL";
        return "OL" + buildASKItems();
    }

    public String buildASKItems() {
        if(items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(";");
        for(Item item : items.values()) sb.append(item.toOLEntry());
        return sb.toString();
    }

    public static String buildOAPacket(Item item) {
        return "OAKO*O;" + item.toOLEntry();
    }

    public static String buildOCPacket(Item item) {
        return "OCKO*O;" + item.toOLEntry();
    }

    public static String buildOMPacket(Item item) {
        StringBuilder sb = new StringBuilder("OM");
        sb.append(item.getUid()).append('|');
        if(item.getPosition() >= 0) sb.append(item.getPosition());
        return sb.toString();
    }

    public static String buildOQPacket(Item item) {
        return "OQ" + item.getUid() + "|" + item.getQuantity();
    }

    public static String buildORPacket(long uid) {
        return "OR" + uid;
    }

    public static boolean isStackable(ItemTemplate template) {
        if(template == null) return false;
        int typeId = template.getTypeId();
        return typeId >= 48 && typeId != 82 && typeId != 89 && typeId != 113;
    }

    public boolean isValidSlotFor(Item item, int position) {
        if(item == null) return false;
        int typeId = item.getTemplate().getTypeId();
        switch(position) {
            case 0:  return typeId == 1;                  // Amulette
            case 1:  return isWeapon(typeId);             // Arme
            case 2:  return typeId == 9;                  // Anneau gauche
            case 3:  return typeId == 10;                 // Ceinture
            case 4:  return typeId == 9;                  // Anneau droit
            case 5:  return typeId == 11;                 // Bottes
            case 6:  return typeId == 16;                 // Coiffe
            case 7:  return typeId == 17;                 // Cape
            case 8:  return typeId == 18;                 // Familier
            case 9: case 10: case 11:
            case 12: case 13: case 14:
                return typeId == 23;                      // Dofus / trophee ancien slot oeuf
            case 15: return typeId == 82;                 // Bouclier
            default: return false;
        }
    }

    private int defaultSlotFor(Item item) {
        int typeId = item.getTemplate().getTypeId();
        if(typeId == 9) {
            if(getEquipped(2) == null || getEquipped(2) == item) return 2;
            return 4;
        }
        if(typeId == 23) {
            for(int slot = 9; slot <= 14; slot++) {
                Item equipped = getEquipped(slot);
                if(equipped == null || equipped == item) return slot;
            }
            return -1;
        }
        return defaultSlotFor(typeId);
    }

    private static int defaultSlotFor(int typeId) {
        switch(typeId) {
            case 1:  return 0;  // Amulette
            case 2:  return 1;  // Arc
            case 3:  return 1;  // Baguette
            case 4:  return 1;  // Baton
            case 5:  return 1;  // Dague
            case 6:  return 1;  // Epee
            case 7:  return 1;  // Marteau
            case 8:  return 1;  // Pelle
            case 9:  return 2;  // Anneau
            case 10: return 3;  // Ceinture
            case 11: return 5;  // Bottes
            case 16: return 6;  // Coiffe
            case 17: return 7;  // Cape
            case 18: return 8;  // Familier
            case 19: return 1;  // Hache
            case 23: return 9;  // Dofus
            case 82: return 15; // Bouclier
            default: return -1;
        }
    }

    private static boolean isWeapon(int typeId) {
        return typeId >= 2 && typeId <= 8 || typeId == 19;
    }

    public void load(java.util.List<Item> loaded) {
        items.clear();
        long maxUid = 0L;
        for(Item item : loaded) {
            items.put(item.getUid(), item);
            if(item.getUid() > maxUid) maxUid = item.getUid();
        }
        long next = maxUid + 1L;
        while(next > UID_GEN.get()) {
            if(UID_GEN.compareAndSet(UID_GEN.get(), next)) break;
        }
    }
}
