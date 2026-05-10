package org.dofus.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service de régénération de vie (hors combat).
 *
 * Mécanisme Dofus 1.29 :
 *   - Un personnage commence à regagner de la vie 10 secondes après le dernier combat/dégât.
 *   - Il regagne {@code floor(baseLife/10)} PV par tick de 2 secondes (configurable).
 *   - La régénération s'arrête dès que la vie est pleine.
 *   - En combat, la régénération est suspendue.
 *
 * Paquet de mise à jour PV : {@code AS{life}~{maxLife}}
 * Paquet de début de regen : {@code AS1~1}  (TODO : vérifier le paquet exact Dofus 1.29)
 *
 * Utilisation :
 *   {@code RegenService.start(character)}  — déclenche la regen (après sortie de combat)
 *   {@code RegenService.stop(character)}   — annule la regen (entrée en combat, déconnexion)
 *   {@code RegenService.init()}            — à appeler au démarrage
 *   {@code RegenService.shutdown()}        — à appeler à l'arrêt du serveur
 */
public class RegenService {

    private static final Logger logger = LoggerFactory.getLogger(RegenService.class);

    /** Délai avant démarrage de la regen (secondes). */
    private static final int START_DELAY_SEC = 10;

    /** Intervalle entre chaque tick de regen (secondes). */
    private static final int TICK_SEC = 2;

    private static final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "regen");
            t.setDaemon(true);
            return t;
        });

    /** characterId → future du tick de regen */
    private static final Map<Integer, ScheduledFuture<?>> regens = new ConcurrentHashMap<>();

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Déclenche (ou redémarre) la régénération d'un personnage.
     * Le personnage doit être connecté et hors combat.
     */
    public static void start(Characters character) {
        stop(character); // annule toute regen en cours

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> tick(character),
            START_DELAY_SEC, TICK_SEC, TimeUnit.SECONDS
        );
        regens.put(character.getId(), future);
        logger.debug("RegenService : regen démarrée pour {}", character.getName());
    }

    /**
     * Arrête la régénération d'un personnage (entrée en combat, déconnexion, mort).
     */
    public static void stop(Characters character) {
        if(character == null) return;
        ScheduledFuture<?> f = regens.remove(character.getId());
        if(f != null && !f.isDone()) {
            f.cancel(false);
            logger.debug("RegenService : regen annulée pour {}", character.getName());
        }
    }

    /** Arrête toutes les régens et shutdown le scheduler. */
    public static void shutdown() {
        for(ScheduledFuture<?> f : regens.values()) f.cancel(false);
        regens.clear();
        scheduler.shutdown();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    private static void tick(Characters character) {
        try {
            if(!character.isConnected()) {
                stop(character);
                return;
            }

            short currentLife = character.getLife();
            short maxLife     = character.getLifeMax(); // base + 5*(lvl-1) + vitalité
            if(currentLife >= maxLife) {
                stop(character);
                return;
            }

            // PV regagnés par tick : max(1, floor(maxLife / 10)) par tranche de 2 secondes
            short regen = (short) Math.max(1, maxLife / 10);
            short newLife = (short) Math.min(maxLife, currentLife + regen);
            character.setLife(newLife);

            // Notifie le client
            IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
            if(session != null && session.isConnected()) {
                // Paquet de mise à jour vie — TODO : vérifier format exact
                session.write("AS" + newLife + "~" + maxLife);
            }

            if(newLife >= maxLife) {
                stop(character); // vie pleine → arrêt
            }
        } catch(Exception e) {
            logger.warn("RegenService tick error for {}: {}", character.getName(), e.getMessage());
            stop(character);
        }
    }
}
