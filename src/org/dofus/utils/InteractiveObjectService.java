package org.dofus.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.InteractiveObjectsData;
import org.dofus.database.objects.InteractiveObjectsData.InteractiveObjectTemplate;
import org.dofus.database.objects.ItemsData;
import org.dofus.network.game.handlers.parsers.WaypointParser;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemTemplate;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;

/**
 * Gestion des objets interactifs de map (ressources, zaaps, ateliers).
 * Le serveur s'appuie sur les cellules decodees et interactive_objects_data :
 * pas de table d'apprentissage parallele pour la walkability.
 */
public final class InteractiveObjectService {

    private static final long DEFAULT_INTERACTION_DURATION_MS = 1200L;
    private static final long DEFAULT_RESPAWN_DELAY_MS = 30000L;
    private static final ConcurrentMap<String, Long> cooldownUntil = new ConcurrentHashMap<String, Long>();
    private static final Map<Integer, HarvestReward> HARVEST_REWARDS = harvestRewards();
    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "interactive-objects");
            thread.setDaemon(true);
            return thread;
        });

    private InteractiveObjectService() {}

    public static boolean use(Characters character, IoSession session, short cellId, int skillId) {
        if(character == null || session == null || character.getCurrentMap() == null) return false;

        MapTemplate map = character.getCurrentMap();
        Cell cell = map.getCell(cellId);
        boolean hasDecodedObject = cell != null && (cell.hasInteractiveObject() || cell.hasKnownInteractiveObject());
        boolean hasOfficialObject = map.isOfficialInteractiveCell(cellId);
        if(!hasDecodedObject && skillId < 0) return false;

        int gfxId = map.getOfficialInteractiveGfx(cellId);
        InteractiveObjectTemplate data = gfxId > 0 ? InteractiveObjectsData.get(gfxId) : null;
        if(data != null && !data.acceptsSkill(skillId)) {
            session.write("BN");
            return true;
        }

        if(!hasDecodedObject && !hasOfficialObject && skillId >= 0) {
            session.write("BN");
            return true;
        }

        if(handleImmediateSkill(character, session, skillId)) return true;

        long durationMs = data != null && data.getDurationMs() > 0 ? data.getDurationMs() : DEFAULT_INTERACTION_DURATION_MS;
        long respawnMs = data != null && data.getRespawnMs() > 0 ? data.getRespawnMs() : DEFAULT_RESPAWN_DELAY_MS;
        int unknown = data != null ? data.getUnknown() : 0;
        HarvestReward reward = getReward(skillId, data);
        if(reward != null && reward.respawnMs > 0) respawnMs = reward.respawnMs;

        if(!isReachable(character.getCurrentCell(), cellId)) {
            session.write("BN");
            return true;
        }

        String key = map.getId() + ":" + cellId;
        long now = System.currentTimeMillis();
        Long cooldown = cooldownUntil.get(key);
        if(cooldown != null && cooldown.longValue() > now) {
            session.write("BN");
            return true;
        }

        cooldownUntil.put(key, now + Math.max(1L, respawnMs));
        broadcast(map, "GA;501;" + character.getId() + ";" + cellId + "," + durationMs + "," + unknown);
        broadcast(map, "GDF||" + cellId + ";2;0");

        if(reward != null) {
            scheduler.schedule(() -> grantReward(character, session, reward), Math.max(1L, durationMs), TimeUnit.MILLISECONDS);
        } else if(skillId == 153) {
            session.write("BN");
        }

        scheduler.schedule(() -> {
            cooldownUntil.remove(key);
            broadcast(map, "GDF||" + cellId + ";1;1");
        }, Math.max(1L, respawnMs), TimeUnit.MILLISECONDS);

        return true;
    }

    public static boolean isReady(int mapId, short cellId) {
        Long cooldown = cooldownUntil.get(mapId + ":" + cellId);
        return cooldown == null || cooldown.longValue() <= System.currentTimeMillis();
    }

    private static boolean handleImmediateSkill(Characters character, IoSession session, int skillId) {
        if(skillId == 114) {
            WaypointParser.panelZaaps(character, session);
            return true;
        }
        if(skillId == 157) {
            WaypointParser.panelZaapis(character, session);
            return true;
        }
        if(skillId == 44) {
            WaypointParser.saveZaap(character, session);
            return true;
        }
        return false;
    }

    private static boolean isReachable(short fromCell, short targetCell) {
        if(fromCell == targetCell) return true;
        int diff = targetCell - fromCell;
        if(diff == -15 || diff == -14 || diff == 14 || diff == 15) return true;
        return MapCellDecoder.distance(fromCell, targetCell) <= 2;
    }

    private static void broadcast(MapTemplate map, String packet) {
        for(Characters actor : new ArrayList<Characters>(map.getActors().values())) {
            IoSession session = WorldData.getSessionByAccount().get(actor.getOwner());
            if(session != null && session.isConnected()) session.write(packet);
        }
    }

    private static HarvestReward getReward(int skillId, InteractiveObjectTemplate data) {
        HarvestReward reward = HARVEST_REWARDS.get(skillId);
        if(reward == null) return null;
        if(data != null && !data.acceptsSkill(skillId)) return null;
        return reward;
    }

    private static void grantReward(Characters character, IoSession session, HarvestReward reward) {
        if(character == null || reward == null) return;
        int templateId = reward.randomTemplateId();
        ItemTemplate template = ItemsData.getTemplate(templateId);
        if(template == null) {
            if(session != null && session.isConnected()) session.write("BN");
            return;
        }

        int quantity = ThreadLocalRandom.current().nextInt(reward.minQuantity, reward.maxQuantity + 1);
        Item stacked = findStackable(character.getInventory(), template);
        Item item = character.getInventory().addItem(template, quantity);
        if(stacked != null && stacked.getUid() == item.getUid()) ItemsData.update(item);
        else ItemsData.insert(character.getId(), item);

        if(session != null && session.isConnected()) {
            session.write(Inventory.buildOAPacket(item));
            session.write("Im021;" + quantity + "~" + templateId);
            session.write("Ow" + character.getInventory().getUsedPods() + "|" + character.getMaxPods());
        }
    }

    private static Item findStackable(Inventory inventory, ItemTemplate template) {
        if(inventory == null || !Inventory.isStackable(template)) return null;
        for(Item item : inventory.getBag()) {
            if(item.getTemplate().getId() == template.getId()) return item;
        }
        return null;
    }

    private static Map<Integer, HarvestReward> harvestRewards() {
        Map<Integer, HarvestReward> rewards = new HashMap<Integer, HarvestReward>();
        putReward(rewards, 45, 289, 1, 5, 300000);   // Ble
        putReward(rewards, 53, 400, 1, 5, 540000);   // Orge
        putReward(rewards, 57, 533, 1, 5, 600000);   // Avoine
        putReward(rewards, 46, 401, 1, 5, 660000);   // Houblon
        putReward(rewards, 50, 423, 1, 5, 300000);   // Lin paysan
        putReward(rewards, 52, 532, 1, 5, 780000);   // Seigle
        putReward(rewards, 58, 405, 1, 5, 1140000);  // Malt
        putReward(rewards, 54, 425, 1, 5, 300000);   // Chanvre paysan
        putReward(rewards, 159, 7018, 1, 5, 900000); // Riz
        putReward(rewards, 68, 421, 1, 5, 300000);   // Lin alchimiste
        putReward(rewards, 69, 428, 1, 5, 300000);   // Chanvre alchimiste
        putReward(rewards, 71, 395, 1, 5, 240000);   // Trefle
        putReward(rewards, 72, 380, 1, 5, 240000);   // Menthe
        putReward(rewards, 73, 593, 1, 5, 240000);   // Orchidee
        putReward(rewards, 74, 594, 1, 5, 420000);   // Edelweiss
        putReward(rewards, 160, 7059, 1, 5, 240000); // Graine de pandouille
        putReward(rewards, 6, 303, 1, 5, 300000);    // Frene
        putReward(rewards, 39, 473, 1, 5, 600000);   // Chataignier
        putReward(rewards, 40, 476, 1, 5, 900000);   // Noyer
        putReward(rewards, 10, 460, 1, 5, 1020000);  // Chene
        putReward(rewards, 139, 2358, 1, 5, 2400000);// Bombu
        putReward(rewards, 141, 2357, 1, 5, 2400000);// Oliviolet
        putReward(rewards, 37, 471, 1, 5, 2400000);  // Erable
        putReward(rewards, 33, 461, 1, 5, 2400000);  // If
        putReward(rewards, 154, 7013, 1, 5, 2400000);// Bambou
        putReward(rewards, 41, 474, 1, 5, 2400000);  // Merisier
        putReward(rewards, 34, 449, 1, 5, 3300000);  // Ebene
        putReward(rewards, 174, 7925, 1, 5, 1200000);// Kalyptus
        putReward(rewards, 38, 472, 1, 5, 3600000);  // Charme
        putReward(rewards, 155, 7016, 1, 5, 1800000);// Bambou sombre
        putReward(rewards, 35, 470, 1, 5, 7200000);  // Orme
        putReward(rewards, 158, 7014, 1, 5, 10800000);// Bambou sacre
        putReward(rewards, 24, 312, 1, 5, 420000);   // Fer
        putReward(rewards, 25, 441, 1, 5, 780000);   // Cuivre
        putReward(rewards, 26, 442, 1, 5, 900000);   // Bronze
        putReward(rewards, 28, 443, 1, 5, 1080000);  // Kobalte
        putReward(rewards, 56, 445, 1, 5, 1080000);  // Manganese
        putReward(rewards, 55, 444, 1, 5, 1200000);  // Etain
        putReward(rewards, 162, 7032, 1, 5, 1500000);// Silicate
        putReward(rewards, 29, 350, 1, 5, 1800000);  // Argent
        putReward(rewards, 31, 446, 1, 5, 2100000);  // Bauxite
        putReward(rewards, 30, 313, 1, 5, 2100000);  // Or
        putReward(rewards, 161, 7033, 1, 5, 180000); // Dolomite
        putReward(rewards, 136, new int[] {2187}, 1, 3, 180000);
        putReward(rewards, 140, new int[] {1759}, 1, 3, 180000);
        putReward(rewards, 124, new int[] {1782, 1844, 603}, 1, 3, 180000);
        putReward(rewards, 125, new int[] {1844, 603, 1847, 1794}, 1, 3, 180000);
        putReward(rewards, 126, new int[] {603, 1847, 1794, 1779}, 1, 3, 180000);
        putReward(rewards, 127, new int[] {1847, 1794, 1779, 1801}, 1, 3, 180000);
        putReward(rewards, 128, new int[] {598, 1757, 1750}, 1, 3, 180000);
        putReward(rewards, 129, new int[] {1757, 1805, 600}, 1, 3, 180000);
        putReward(rewards, 130, new int[] {1805, 1750, 1784, 600}, 1, 3, 180000);
        putReward(rewards, 131, new int[] {600, 1805, 602, 1784}, 1, 3, 180000);
        putReward(rewards, 102, 311, 1, 10, 120000); // Puiser
        putReward(rewards, 42, 537, 1, 5, 600000);   // Tas de patates
        return Collections.unmodifiableMap(rewards);
    }

    private static void putReward(Map<Integer, HarvestReward> rewards, int skillId, int itemTemplateId,
            int minQuantity, int maxQuantity, long respawnMs) {
        rewards.put(skillId, new HarvestReward(itemTemplateId, minQuantity, maxQuantity, respawnMs));
    }

    private static void putReward(Map<Integer, HarvestReward> rewards, int skillId, int[] itemTemplateIds,
            int minQuantity, int maxQuantity, long respawnMs) {
        rewards.put(skillId, new HarvestReward(itemTemplateIds, minQuantity, maxQuantity, respawnMs));
    }

    private static final class HarvestReward {
        private final int[] itemTemplateIds;
        private final int minQuantity;
        private final int maxQuantity;
        private final long respawnMs;

        private HarvestReward(int itemTemplateId, int minQuantity, int maxQuantity, long respawnMs) {
            this(new int[] { itemTemplateId }, minQuantity, maxQuantity, respawnMs);
        }

        private HarvestReward(int[] itemTemplateIds, int minQuantity, int maxQuantity, long respawnMs) {
            this.itemTemplateIds = itemTemplateIds != null && itemTemplateIds.length > 0 ? itemTemplateIds : new int[] { 0 };
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.respawnMs = respawnMs;
        }

        private int randomTemplateId() {
            if(itemTemplateIds.length == 1) return itemTemplateIds[0];
            return itemTemplateIds[ThreadLocalRandom.current().nextInt(itemTemplateIds.length)];
        }
    }
}
