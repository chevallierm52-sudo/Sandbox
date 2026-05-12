package org.dofus.utils;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.InteractiveObjectCellsData;
import org.dofus.database.objects.InteractiveObjectsData;
import org.dofus.database.objects.InteractiveObjectsData.InteractiveObjectTemplate;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;

/**
 * Gestion minimale des objets interactifs de map (ressources, portes, ateliers).
 * Les metiers/drops viendront se brancher ici, mais le protocole client GA500/GDF
 * est deja centralise pour eviter les interactions sur des cellules invalides.
 */
public final class InteractiveObjectService {

    private static final long DEFAULT_INTERACTION_DURATION_MS = 1200L;
    private static final long DEFAULT_RESPAWN_DELAY_MS = 30000L;
    private static final ConcurrentMap<String, Long> cooldownUntil = new ConcurrentHashMap<String, Long>();
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
        boolean knownCell = InteractiveObjectCellsData.isKnown(map.getId(), cellId);
        boolean hasDecodedObject = cell != null && (cell.hasInteractiveObject() || cell.hasKnownInteractiveObject());
        if(!knownCell && !hasDecodedObject && skillId < 0) return false;

        if(!knownCell && !hasDecodedObject) {
            InteractiveObjectCellsData.learn(map.getId(), cellId, skillId, isBlockingSkill(skillId));
        }

        InteractiveObjectTemplate data = cell != null ? InteractiveObjectsData.get(cell.getLayerObject2Num()) : null;
        long durationMs = data != null && data.getDurationMs() > 0 ? data.getDurationMs() : DEFAULT_INTERACTION_DURATION_MS;
        long respawnMs = data != null && data.getRespawnMs() > 0 ? data.getRespawnMs() : DEFAULT_RESPAWN_DELAY_MS;

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

        cooldownUntil.put(key, now + respawnMs);
        broadcast(map, "GA;501;" + character.getId() + ";" + cellId + "," + durationMs);
        broadcast(map, "GDF||" + cellId + ";2;0");

        scheduler.schedule(() -> {
            cooldownUntil.remove(key);
            broadcast(map, "GDF||" + cellId + ";1;1");
        }, respawnMs, TimeUnit.MILLISECONDS);

        return true;
    }

    private static boolean isBlockingSkill(int skillId) {
        // 102 correspond au puits dans les logs actuels. Les recoltes inconnues
        // sont apprises comme traversables pour ne pas bloquer les champs.
        return skillId == 102;
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
}
