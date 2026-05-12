package org.dofus.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service de réapparition des groupes de monstres tués.
 *
 * Comportement Dofus 1.29 :
 *   - Après qu'un groupe de monstres est tué, il réapparaît sur la même map
 *     après {@value #DEFAULT_RESPAWN_SEC} secondes (configurable).
 *   - Le nouveau groupe a la même composition que l'original.
 *   - L'ID du groupe est recalculé (incrément unique).
 *
 * Utilisation :
 *   {@code MapRespawnService.scheduleRespawn(map, killedGroup)}
 *   {@code MapRespawnService.shutdown()} — à appeler dans {@code Main.stop()}
 */
public class MapRespawnService {

    private static final Logger logger = LoggerFactory.getLogger(MapRespawnService.class);

    /** Délai par défaut de réapparition en secondes (10 minutes). */
    public static final int DEFAULT_RESPAWN_SEC = 600;

    private static final AtomicInteger GROUP_ID_GEN = new AtomicInteger(1000);

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "respawn");
            t.setDaemon(true);
            return t;
        });

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Planifie le réapparition d'un groupe de monstres sur sa map d'origine.
     *
     * @param map   La map sur laquelle le groupe doit réapparaître
     * @param group Le groupe tué (sert de template pour la recomposition)
     */
    public static void scheduleRespawn(MapTemplate map, MonsterGroup group) {
        scheduler.schedule(() -> respawn(map, group), DEFAULT_RESPAWN_SEC, TimeUnit.SECONDS);
        logger.debug("Respawn planifié dans {}s sur map {} (groupe {})",
            new Object[] { DEFAULT_RESPAWN_SEC, map.getId(), group.getId()});
    }

    /**
     * Planifie un réapparition avec un délai personnalisé.
     */
    public static void scheduleRespawn(MapTemplate map, MonsterGroup group, int delaySec) {
        scheduler.schedule(() -> respawn(map, group), delaySec, TimeUnit.SECONDS);
    }

    /** Arrête le scheduler (appelé dans Main.stop()). */
    public static void shutdown() {
        scheduler.shutdown();
        logger.info("MapRespawnService arrêté");
    }

    // ── Respawn ───────────────────────────────────────────────────────────────

    private static void respawn(MapTemplate map, MonsterGroup original) {
        try {
            // Crée un nouveau groupe avec le même template de composition
            int newId = GROUP_ID_GEN.getAndIncrement();
            short requestedCell = original.getCell();
            Short naturalCell = map.findNearestValidMonsterCell(requestedCell);
            if(naturalCell == null) {
                logger.warn("Respawn ignoré sur map {} cellule {} : aucune cellule naturelle valide trouvée",
                    map.getId(), requestedCell);
                return;
            }
            MonsterGroup newGroup = new MonsterGroup(newId, naturalCell.shortValue(), original.getOrientation());

            if(naturalCell.shortValue() != requestedCell) {
                logger.debug("Respawn déplacé naturellement sur map {} : {} -> {}",
                    new Object[] { map.getId(), requestedCell, naturalCell });
            }

            // Copie les membres (même monstres, mêmes grades, PV restaurés)
            for(MonsterGroup.MonsterEntry entry : original.getMembers()) {
                newGroup.addMember(entry.getTemplate(), entry.getGrade());
            }

            map.addMonsterGroup(newGroup);

            // Notifie les joueurs présents sur la map (paquet GM|+)
            // Format monstre dans GM : cell;dir;0;groupId;monsterEntries;0;-1;0;0;0;|
            String gmEntry = newGroup.toGMEntry();
            for(org.dofus.objects.actors.Characters actor : new java.util.ArrayList<>(map.getActors().values())) {
                org.apache.mina.core.session.IoSession sess =
                    org.dofus.objects.WorldData.getSessionByAccount().get(actor.getOwner());
                if(sess != null && sess.isConnected()) {
                    sess.write("GM|+" + gmEntry);
                }
            }

            logger.debug("Respawn effectué sur map {} : groupe {} ({} monstres)",
               new Object[] { map.getId(), newId, newGroup.getMembers().size()});

        } catch(Exception e) {
            logger.warn("Erreur lors du respawn sur map {}: {}", map.getId(), e.getMessage());
        }
    }
}
