package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.ItemsData;
import java.util.ArrayList;
import java.util.List;

import org.dofus.network.game.GameClient;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemEffect;
import org.dofus.objects.items.ItemTemplate;
import org.dofus.objects.items.PetService;
import org.dofus.objects.items.UseItemService;
import org.dofus.objects.characters.Statistic;
import org.dofus.utils.PacketValidator;
import org.dofus.utils.GroundItemService;
import org.dofus.utils.RegenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur d'inventaire Dofus 1.29 (prefixe 'O').
 *
 * Client -> Serveur officiel 1.29 :
 *   OM{uid}|{position}[|quantity]  - deplacer/equiper/desequiper
 *   OD{uid}|{qty}                  - jeter au sol
 *   Od{uid}|{qty}                  - detruire
 *   OU{uid}|... / Ou{uid}|...      - utiliser / confirmer utilisation
 *
 * Serveur -> Client :
 *   OAKO*O;{entry}                 - ajout objet
 *   OM{uid}|{position}             - mouvement
 *   OR{uid}                        - suppression
 *   OQ{uid}|{quantity}             - changement quantite
 *   Ow{used}|{max}                 - pods
 */
public class InventoryParser {

    private static final Logger logger = LoggerFactory.getLogger(InventoryParser.class);
    private static final int[] LIVING_ITEM_LEVEL_STEPS = {
        0, 10, 21, 33, 46, 60, 75, 91, 108, 126, 145, 165, 186, 208, 231, 255, 280, 306, 333, 361
    };

    public static void parse(Characters character, IoSession session, GameClient client, String packet) {
        if(packet == null || packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'M': moveItem(character, session, packet.substring(2));    break;
            case 'D': dropItem(character, session, packet.substring(2));    break;
            case 'd': destroyItem(character, session, packet.substring(2)); break;
            case 'U': useItem(character, session, client, packet.substring(2), false); break;
            case 'u': useItem(character, session, client, packet.substring(2), true);  break;
            case 'x': dissociateLivingItem(character, session, packet.substring(2)); break;
            case 's': setLivingItemSkin(character, session, packet.substring(2));    break;
            case 'f': feedLivingItem(character, session, packet.substring(2));       break;

            // Compatibilite avec l'ancien parseur interne.
            case 'e': equipItemDefault(character, session, packet.substring(2)); break;
            default:
                logger.debug("InventoryParser : paquet inconnu : {}", packet);
        }
    }

    private static void moveItem(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        if(parts.length < 2) return;

        long uid;
        int requestedPosition;
        try {
            uid = Long.parseLong(parts[0]);
            requestedPosition = parts[1].trim().isEmpty() ? -1 : Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid)) return;

        Inventory inv = character.getInventory();
        Item item = inv.getByUid(uid);
        if(item == null) return;

        /*
         * Nourrir un familier en 1.29 passe par le meme paquet que l'equipement :
         * OM{uidNourriture}|8 quand on glisse une ressource sur le slot familier.
         * Sans cette interception, le serveur essaye d'equiper la ressource en position 8
         * et repond Im11, ce qui explique les nourritures refusees dans les logs.
         */
        if(requestedPosition == 8 && !PetService.isPet(item) && !item.isEquipped()) {
            Item equippedPet = inv.getEquipped(8);
            if(equippedPet != null && PetService.feed(character, session, equippedPet, item)) {
                broadcastAccessories(character);
                return;
            }
        }

        int oldPosition = item.getPosition();
        if(isLivingTemplate(item) && requestedPosition >= 0) {
            associateLivingItem(character, session, inv, item, requestedPosition);
            return;
        }

        String failureMessage = inv.getEquipFailureMessage(character, item, requestedPosition);
        if(failureMessage != null) {
            session.write(failureMessage);
            logger.debug("{} mouvement item refuse uid={} pos={} msg={}", new Object[] { character.getName(), uid, requestedPosition, failureMessage });
            return;
        }

        Item displaced = inv.move(character, uid, requestedPosition);

        if(item.getPosition() == oldPosition && oldPosition != requestedPosition) {
            logger.debug("{} mouvement item refuse uid={} pos={}", new Object[] { character.getName(), uid, requestedPosition });
            return;
        }

        session.write(Inventory.buildOMPacket(item));
        ItemsData.update(item);

        if(displaced != null) {
            session.write(Inventory.buildOMPacket(displaced));
            ItemsData.update(displaced);
        }

        for(Item autoUnequipped : inv.consumeAutoUnequipped()) {
            session.write(Inventory.buildOMPacket(autoUnequipped));
            ItemsData.update(autoUnequipped);
            if(requestedPosition == 15) session.write("Im78");
            else if(requestedPosition == 1) session.write("Im79");
        }

        if(oldPosition >= 0 || item.getPosition() >= 0 || displaced != null) {
            broadcastAccessories(character);
            RegenService.refresh(character, session);
        }

        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        logger.debug("{} deplace item uid={} pos={}", new Object[] { character.getName(), uid, item.getPosition() });
    }

    private static void equipItemDefault(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        long uid;
        try { uid = Long.parseLong(parts[0]); }
        catch(NumberFormatException e) { return; }

        Inventory inv = character.getInventory();
        Item item = inv.getByUid(uid);
        if(item == null || item.isEquipped()) return;

        int targetSlot = -1;
        for(int slot = 0; slot <= 15; slot++) {
            if(inv.isValidSlotFor(item, slot)) { targetSlot = slot; break; }
        }
        String failureMessage = targetSlot >= 0 ? inv.getEquipFailureMessage(character, item, targetSlot) : "Im11";
        if(failureMessage != null) {
            session.write(failureMessage);
            return;
        }

        Item displaced = inv.equip(character, uid);
        if(item.getPosition() < 0) return;

        session.write(Inventory.buildOMPacket(item));
        ItemsData.update(item);

        if(displaced != null) {
            session.write(Inventory.buildOMPacket(displaced));
            ItemsData.update(displaced);
        }

        for(Item autoUnequipped : inv.consumeAutoUnequipped()) {
            session.write(Inventory.buildOMPacket(autoUnequipped));
            ItemsData.update(autoUnequipped);
            if(item.getPosition() == 15) session.write("Im78");
            else if(item.getPosition() == 1) session.write("Im79");
        }

        broadcastAccessories(character);
        RegenService.refresh(character, session);
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
    }

    private static void broadcastAccessories(Characters character) {
        if(character == null || character.getCurrentMap() == null) return;
        String packet = Inventory.buildOaPacket(character);
        if(packet == null) return;
        for(Characters actor : new ArrayList<>(character.getCurrentMap().getActors().values())) {
            IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
            if(actorSession != null && actorSession.isConnected()) actorSession.write(packet);
        }
    }

    private static void destroyItem(Characters character, IoSession session, String args) {
        removeFromInventory(character, session, args, false);
    }

    /**
     * OD{uid}|{qty} - jette un objet au sol.
     * On cherche une des 4 cellules vraiment autour du joueur en grille iso.
     */
    private static void dropItem(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        if(parts.length < 2) return;

        long uid;
        int qty;
        try {
            uid = Long.parseLong(parts[0]);
            qty = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid) || qty <= 0) return;

        Inventory inv = character.getInventory();
        Item item = inv.getByUid(uid);
        if(item == null) return;
        if(item.isEquipped()) {
            session.write("Im1129"); // Vous ne pouvez pas poser ceci au sol.
            return;
        }

        int droppedQty = Math.min(qty, item.getQuantity());
        if(droppedQty <= 0) return;

        Item groundCopy = droppedQty >= item.getQuantity()
            ? item
            : new Item(Inventory.nextUid(), item.getTemplate(), droppedQty, -1, item.getRolledEffects());

        if(GroundItemService.dropNear(character, groundCopy) == null) {
            session.write("Im1145"); // Il n'y a pas assez de place ici.
            logger.debug("{} ne peut pas jeter item uid={} : aucune cellule adjacente libre",
                character.getName(), uid);
            return;
        }

        boolean removed = inv.removeItem(uid, droppedQty);
        if(!removed) return;

        if(inv.getByUid(uid) == null) {
            session.write(Inventory.buildORPacket(uid));
            ItemsData.delete(uid);
        } else {
            session.write(Inventory.buildOQPacket(item));
            ItemsData.update(item);
        }

        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        logger.debug("{} jette {} x item uid={} au sol", new Object[] {
            character.getName(), droppedQty, uid
        });
    }

    private static void removeFromInventory(Characters character, IoSession session, String args, boolean fromDropPacket) {
        String[] parts = args.split("\\|");
        if(parts.length < 2) return;

        long uid;
        int qty;
        try {
            uid = Long.parseLong(parts[0]);
            qty = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid) || qty <= 0) return;

        Inventory inv = character.getInventory();
        Item item = inv.getByUid(uid);
        if(item == null || item.isEquipped()) return;

        boolean removed = inv.removeItem(uid, qty);
        if(!removed) return;

        if(inv.getByUid(uid) == null) {
            session.write(Inventory.buildORPacket(uid));
            ItemsData.delete(uid);
        } else {
            session.write(Inventory.buildOQPacket(item));
            ItemsData.update(item);
        }

        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        logger.debug("{} {} {} x item uid={}", new Object[] {
            character.getName(), fromDropPacket ? "jette" : "detruit", qty, uid
        });
    }

    private static void useItem(Characters character, IoSession session, GameClient client, String args, boolean confirmed) {
        String[] parts = args.split("\\|");
        if(parts.length < 1) return;

        long uid;
        try { uid = Long.parseLong(parts[0]); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid)) return;

        Item item = character.getInventory().getByUid(uid);
        if(item == null || item.isEquipped()) return;

        if(openObvijevanGift(character, session, item)) return;

        if(UseItemService.use(character, session, client, item)) return;

        logger.debug("{} demande utilisation item uid={} confirmed={} : aucune action use_item_actions", new Object[] {
            character.getName(), uid, confirmed
        });
        session.write("BN");
    }

    private static boolean openObvijevanGift(Characters character, IoSession session, Item gift) {
        int obvijevanTemplateId = obvijevanTemplateFromGift(gift.getTemplate().getId());
        if(obvijevanTemplateId < 0) return false;

        ItemTemplate obvijevanTemplate = ItemsData.getTemplate(obvijevanTemplateId);
        if(obvijevanTemplate == null) {
            session.write("Im11");
            return true;
        }

        Inventory inv = character.getInventory();
        inv.removeItem(gift.getUid(), 1);
        Item remainingGift = inv.getByUid(gift.getUid());
        if(remainingGift == null) {
            ItemsData.delete(gift.getUid());
            session.write(Inventory.buildORPacket(gift.getUid()));
        } else {
            ItemsData.update(remainingGift);
            session.write(Inventory.buildOQPacket(remainingGift));
        }

        Item obvijevan = Item.create(Inventory.nextUid(), obvijevanTemplate, 1, -1);
        Item stored = inv.addExisting(obvijevan);
        if(stored == obvijevan) {
            ItemsData.insert(character.getId(), stored);
            session.write(Inventory.buildOAPacket(stored));
        } else {
            ItemsData.update(stored);
            session.write(Inventory.buildOQPacket(stored));
        }

        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        return true;
    }

    private static int obvijevanTemplateFromGift(int giftTemplateId) {
        switch(giftTemplateId) {
            case 9360: return 9255; // Cadeau Amulette Obvijevan
            case 9361: return 9256; // Cadeau Anneau Obvijevan
            case 9362: return 9233; // Cadeau Cape Obvijevan
            case 9363: return 9234; // Cadeau Chapeau Obvijevan
            default:   return -1;
        }
    }

    private static void associateLivingItem(Characters character, IoSession session, Inventory inv,
                                           Item livingItem, int requestedPosition) {
        Item target = inv.getEquipped(requestedPosition);
        if(livingItem.isEquipped() || livingItem.getQuantity() != 1
                || target == null || target.hasEffect(970)
                || !isCompatibleLivingTarget(livingItem, target)) {
            session.write("Im11");
            return;
        }

        // Conserver l'état de l'Obvijevan détaché quand on le rattache.
        // Bug avant patch : le rattachement remettait skin=1 et xp=0,
        // ce qui faisait perdre les skins débloqués après Ox puis OM.
        int livingMood = livingItem.getEffectParam3(971);
        int livingSkin = livingItem.getEffectParam3(972);
        int livingType = livingItem.getEffectParam3(973);
        int livingXp   = livingItem.getEffectParam3(974);

        if(livingMood == Integer.MIN_VALUE) livingMood = 1;
        if(livingSkin == Integer.MIN_VALUE) livingSkin = 1;
        if(livingType == Integer.MIN_VALUE || livingType <= 0) livingType = target.getTemplate().getTypeId();
        if(livingXp == Integer.MIN_VALUE) livingXp = 0;

        int maxSkin = livingMaxSkin(livingXp);
        livingSkin = Math.max(1, Math.min(maxSkin, livingSkin));

        target.replaceEffectParam3(970, livingItem.getTemplate().getId());
        target.replaceEffectParam3(971, livingMood);
        target.replaceEffectParam3(972, livingSkin);
        target.replaceEffectParam3(973, target.getTemplate().getTypeId());
        target.replaceEffectParam3(974, livingXp);

        inv.removeItem(livingItem.getUid(), 1);
        ItemsData.delete(livingItem.getUid());
        ItemsData.update(target);

        session.write(Inventory.buildORPacket(livingItem.getUid()));
        session.write(Inventory.buildOCPacket(target));
        broadcastAccessories(character);
        RegenService.refresh(character, session);
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
    }

    private static void dissociateLivingItem(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        if(parts.length < 1) return;

        long uid;
        try { uid = Long.parseLong(parts[0]); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid)) return;

        Inventory inv = character.getInventory();
        Item item = inv.getByUid(uid);
        if(item == null || !item.hasEffect(970)) {
            session.write("Im11");
            return;
        }

        int livingTemplateId = item.getEffectParam3(970);
        ItemTemplate livingTemplate = ItemsData.getTemplate(livingTemplateId);
        if(livingTemplate == null || livingTemplate.getTypeId() != 113) {
            session.write("Im11");
            return;
        }

        Item detachedLivingItem = new Item(
                Inventory.nextUid(),
                livingTemplate,
                1,
                -1,
                buildDetachedLivingEffects(item));

        item.removeLivingEffects();
        inv.addExisting(detachedLivingItem);

        ItemsData.update(item);
        ItemsData.insert(character.getId(), detachedLivingItem);

        session.write(Inventory.buildOCPacket(item));
        session.write(Inventory.buildOAPacket(detachedLivingItem));
        broadcastAccessories(character);
        RegenService.refresh(character, session);
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
    }

    private static void setLivingItemSkin(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        if(parts.length < 3) return;

        long uid;
        int skin;
        try {
            uid = Long.parseLong(parts[0]);
            skin = Integer.parseInt(parts[2]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid)) return;

        Item item = character.getInventory().getByUid(uid);
        if(item == null || !item.hasEffect(970)) {
            session.write("Im11");
            return;
        }

        int maxSkin = livingMaxSkin(item.getEffectParam3(974));
        int normalizedSkin = Math.max(1, Math.min(maxSkin, skin));
        item.replaceEffectParam3(972, normalizedSkin);
        ItemsData.update(item);
        session.write(Inventory.buildOCPacket(item));
        broadcastAccessories(character);
    }

    private static void feedLivingItem(Characters character, IoSession session, String args) {
        Inventory inv = character.getInventory();
        FeedPair pair = resolveFeedPair(inv, args);
        if(pair == null) {
            session.write("Im11");
            return;
        }

        long targetUid = pair.target.getUid();
        long foodUid = pair.food.getUid();
        if(!PacketValidator.validateItemId(session.getId(), (int) targetUid)
                || !PacketValidator.validateItemId(session.getId(), (int) foodUid)) return;

        Item target = pair.target;
        Item food = pair.food;
        if(target != null && (PetService.isPet(target) || PetService.isPetGhost(target))) {
            PetService.feed(character, session, target, food);
            return;
        }

        if(target == null || food == null || !target.hasEffect(970) || food.isEquipped()
                || food.getTemplate().getTypeId() != target.getEffectParam3(973)) {
            session.write("Im11");
            return;
        }

        int oldXp = Math.max(0, target.getEffectParam3(974));
        int gainedXp = livingFoodXp(food);
        int xp = Math.min(LIVING_ITEM_LEVEL_STEPS[LIVING_ITEM_LEVEL_STEPS.length - 1], oldXp + gainedXp);
        target.replaceEffectParam3(974, xp);
        inv.removeItem(foodUid, 1);
        ItemsData.update(target);

        Item remainingFood = inv.getByUid(foodUid);
        if(remainingFood == null) {
            ItemsData.delete(foodUid);
            session.write(Inventory.buildORPacket(foodUid));
        } else {
            ItemsData.update(remainingFood);
            session.write(Inventory.buildOQPacket(remainingFood));
        }

        session.write(Inventory.buildOCPacket(target));
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        session.write("cMKB|0||Â§ Obvijevan : +" + (xp - oldXp) + " XP (skin max " + livingMaxSkin(xp) + ").");
    }

    private static FeedPair resolveFeedPair(Inventory inv, String args) {
        if(inv == null || args == null) return null;

        String[] parts = args.split("\\|");
        if(parts.length >= 2) {
            Item first = firstInventoryItemForToken(inv, parts[0]);
            Item last = firstInventoryItemForToken(inv, parts[parts.length - 1]);
            if(isFeedTarget(first) && last != null && last.getUid() != first.getUid()) {
                return new FeedPair(first, last);
            }
            if(isFeedTarget(last) && first != null && first.getUid() != last.getUid()) {
                return new FeedPair(last, first);
            }
        }

        List<Long> uids = parseFeedUidTokens(args);
        if(uids.size() < 2) return null;

        for(Long uid : uids) {
            Item target = inv.getByUid(uid.longValue());
            if(!isFeedTarget(target)) continue;

            Item food = firstOtherInventoryItem(inv, uids, target.getUid());
            if(food != null) return new FeedPair(target, food);
        }

        Item target = inv.getByUid(uids.get(0).longValue());
        if(target == null) return null;

        Item food = null;
        for(int i = 2; i < uids.size(); i++) {
            food = inv.getByUid(uids.get(i).longValue());
            if(food != null && food.getUid() != target.getUid()) break;
            food = null;
        }
        if(food == null) food = firstOtherInventoryItem(inv, uids, target.getUid());
        return food == null ? null : new FeedPair(target, food);
    }

    private static boolean isFeedTarget(Item item) {
        return item != null && (PetService.isPet(item) || PetService.isPetGhost(item) || item.hasEffect(970));
    }

    private static Item firstOtherInventoryItem(Inventory inv, List<Long> uids, long targetUid) {
        for(int i = uids.size() - 1; i >= 0; i--) {
            Long uid = uids.get(i);
            if(uid.longValue() == targetUid) continue;
            Item item = inv.getByUid(uid.longValue());
            if(item != null) return item;
        }
        return null;
    }

    private static Item firstInventoryItemForToken(Inventory inv, String token) {
        List<Long> candidates = new ArrayList<Long>();
        appendFeedUidToken(candidates, token);
        for(Long uid : candidates) {
            Item item = inv.getByUid(uid.longValue());
            if(item != null) return item;
        }
        return null;
    }

    private static List<Long> parseFeedUidTokens(String args) {
        List<Long> uids = new ArrayList<Long>();
        if(args == null) return uids;
        String[] parts = args.split("\\|");
        for(String part : parts) {
            appendFeedUidToken(uids, part);
        }
        return uids;
    }

    private static void appendFeedUidToken(List<Long> uids, String raw) {
        if(raw == null) return;
        String token = raw.trim();
        if(token.isEmpty()) return;
        Long decimal = null;
        try {
            decimal = Long.valueOf(Long.parseLong(token));
            if(decimal.longValue() > 0) uids.add(decimal);
        } catch(NumberFormatException decimalError) {
            // continue with hexadecimal decoding below
        }

        if(token.matches("[0-9a-fA-F]+")) {
            try {
                Long hexadecimal = Long.valueOf(Long.parseLong(token, 16));
                if(hexadecimal.longValue() > 0
                        && (decimal == null || hexadecimal.longValue() != decimal.longValue())) {
                    uids.add(hexadecimal);
                }
            } catch(NumberFormatException hexError) {
                // ignore malformed ids
            }
        }
    }

    private static final class FeedPair {
        private final Item target;
        private final Item food;

        private FeedPair(Item target, Item food) {
            this.target = target;
            this.food = food;
        }
    }

    private static int livingFoodXp(Item food) {
        if(food == null || food.getTemplate() == null) return 1;

        // Dofus 1.29/Retro : la nourriture doit être du même type,
        // et l'XP gagnée est environ le niveau de l'objet sacrifié / 2.
        // Avant patch, on donnait level entier : 2 items niveau 190 => 380 XP,
        // donc niveau 20 instant avec le cap actuel à 361.
        return Math.max(1, food.getTemplate().getLevel() / 2);
    }

    private static List<ItemEffect> buildDetachedLivingEffects(Item associatedItem) {
        List<ItemEffect> effects = new ArrayList<ItemEffect>();
        appendLivingEffect(effects, 971, associatedItem.getEffectParam3(971));
        appendLivingEffect(effects, 972, associatedItem.getEffectParam3(972));
        appendLivingEffect(effects, 973, associatedItem.getEffectParam3(973));
        appendLivingEffect(effects, 974, associatedItem.getEffectParam3(974));
        return effects;
    }

    private static void appendLivingEffect(List<ItemEffect> effects, int effectId, int value) {
        if(value != Integer.MIN_VALUE) effects.add(ItemEffect.param3(effectId, value));
    }

    private static int livingMaxSkin(int xp) {
        int safeXp = Math.max(0, xp);
        for(int i = 1; i < LIVING_ITEM_LEVEL_STEPS.length; i++) {
            if(safeXp < LIVING_ITEM_LEVEL_STEPS[i]) return i;
        }
        return LIVING_ITEM_LEVEL_STEPS.length;
    }

    private static boolean isLivingTemplate(Item item) {
        return item != null && item.getTemplate().getTypeId() == 113;
    }

    private static boolean isCompatibleLivingTarget(Item livingItem, Item target) {
        if(livingItem == null || target == null) return false;
        int livingTemplate = livingItem.getTemplate().getId();
        int targetType = target.getTemplate().getTypeId();
        switch(livingTemplate) {
            case 9233: return targetType == 17;       // Cape Obvijevan
            case 9234: return targetType == 16;       // Chapeau Obvijevan
            case 9255: return targetType == 1;        // Amulette Obvijevan
            case 9256: return targetType == 9;        // Anneau Obvijevan
            default:   return false;
        }
    }
}
