package org.dofus.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.ItemsData;
import org.dofus.database.objects.MapsData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.GroundItem;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.maps.MapTemplate;

/**
 * Gestion minimale des objets au sol.
 *
 * Phase actuelle : depot au sol + affichage map via GDO + ramassage basique.
 * Phase suivante : expiration + persistence BDD.
 */
public final class GroundItemService {

    private static final int CELL_MIN = 0;
    private static final int CELL_MAX = 559;

    /**
     * Les quatre cellules directement autour du personnage en grille isometrique Dofus 1.29.
     * Ce sont les 4 points visibles autour du perso, pas les cases +/-1 de la liste brute.
     */
    private static final int[] ADJACENT_OFFSETS = { -15, -14, 14, 15 };

    private static final AtomicLong GROUND_ID = new AtomicLong(1L);
    private static final ConcurrentMap<Integer, ConcurrentMap<Short, GroundItem>> BY_MAP = new ConcurrentHashMap<>();

    private GroundItemService() {}

    public static GroundItem dropNear(Characters character, Item item) {
        if(character == null || item == null || character.getCurrentMap() == null) return null;

        MapTemplate map = character.getCurrentMap();
        Short cell = findFreeAdjacentCell(map, character.getCurrentCell());
        if(cell == null) return null;

        GroundItem groundItem = new GroundItem(GROUND_ID.getAndIncrement(), map.getId(), cell, item);
        BY_MAP.computeIfAbsent(map.getId(), k -> new ConcurrentHashMap<Short, GroundItem>()).put(cell, groundItem);

        broadcast(map, groundItem.toGDOPacket());
        return groundItem;
    }

    public static Collection<GroundItem> getForMap(int mapId) {
        Map<Short, GroundItem> items = BY_MAP.get(mapId);
        if(items == null || items.isEmpty()) return java.util.Collections.emptyList();
        return new ArrayList<>(items.values());
    }

    public static GroundItem getAt(int mapId, short cellId) {
        Map<Short, GroundItem> items = BY_MAP.get(mapId);
        if(items == null) return null;
        return items.get(cellId);
    }

    public static GroundItem removeAt(int mapId, short cellId) {
        Map<Short, GroundItem> items = BY_MAP.get(mapId);
        if(items == null) return null;
        GroundItem removed = items.remove(cellId);
        if(removed != null) {
            MapTemplate map = MapsData.findById(mapId);
            if(map != null) broadcast(map, removed.toGDORemovePacket());
        }
        return removed;
    }

    public static boolean hasGroundItem(int mapId, short cellId) {
        Map<Short, GroundItem> items = BY_MAP.get(mapId);
        return items != null && items.containsKey(cellId);
    }

    public static boolean pickup(Characters character, IoSession session, short cellId) {
        if(character == null || session == null || character.getCurrentMap() == null) return false;

        int mapId = character.getCurrentMap().getId();
        GroundItem groundItem = getAt(mapId, cellId);
        if(groundItem == null) return false;

        if(!isPickupReachable(character.getCurrentCell(), cellId)) {
            session.write("BN");
            return false;
        }

        Item item = groundItem.getItem();
        if(item == null) return false;

        Inventory inventory = character.getInventory();
        Item added = inventory.addExisting(item);
        if(added == null) {
            session.write("Im110");
            return false;
        }

        removeAt(mapId, cellId);

        if(added == item) {
            ItemsData.insert(character.getId(), added);
            session.write(Inventory.buildOAPacket(added));
        } else {
            ItemsData.update(added);
            session.write(Inventory.buildOQPacket(added));
        }
        session.write("Ow" + inventory.getUsedPods() + "|" + character.getMaxPods());
        return true;
    }

    private static boolean isPickupReachable(short fromCell, short targetCell) {
        if(fromCell == targetCell) return true;
        int diff = targetCell - fromCell;
        for(int offset : ADJACENT_OFFSETS) {
            if(diff == offset) return true;
        }
        return false;
    }

    private static Short findFreeAdjacentCell(MapTemplate map, short fromCell) {
        List<Short> candidates = new ArrayList<>(ADJACENT_OFFSETS.length);
        for(int offset : ADJACENT_OFFSETS) {
            int cell = fromCell + offset;
            if(cell < CELL_MIN || cell > CELL_MAX) continue;
            if(isHorizontalWrap(fromCell, cell, offset)) continue;
            candidates.add((short) cell);
        }

        for(Short cell : candidates) {
            if(isCellFree(map, cell)) return cell;
        }
        return null;
    }

    private static boolean isHorizontalWrap(short fromCell, int toCell, int offset) {
        // Les offsets officiels utilises ici ne traversent pas une ligne par +/-1.
        return false;
    }

    private static boolean isCellFree(MapTemplate map, short cellId) {
        if(!map.isValidActorCell(cellId, true)) return false;
        if(hasGroundItem(map.getId(), cellId)) return false;

        for(Characters actor : map.getActors().values()) {
            if(actor.getCurrentCell() == cellId) return false;
        }

        for(org.dofus.objects.actors.NPC npc : map.getNpcs().values()) {
            if(npc.getCellId() == cellId) return false;
        }

        for(org.dofus.objects.monsters.MonsterGroup group : map.getMonsterGroups().values()) {
            if(group.getCell() == cellId) return false;
        }

        return true;
    }

    private static void broadcast(MapTemplate map, String packet) {
        for(Characters actor : new ArrayList<>(map.getActors().values())) {
            IoSession session = WorldData.getSessionByAccount().get(actor.getOwner());
            if(session != null && session.isConnected()) session.write(packet);
        }
    }
}
