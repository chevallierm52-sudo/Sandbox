package org.dofus.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Limiteur de débit par session — protection contre le flood de packets.
 *
 * Algorithme : fenêtre glissante d'1 seconde.
 *   - Chaque session a un compteur de packets reçus dans la seconde courante.
 *   - Si le compteur dépasse {@value #MAX_PACKETS_PER_SECOND}, la session est bloquée
 *     pendant {@value #BAN_DURATION_SEC} secondes.
 *   - Après 3 bans consécutifs, la session est fermée définitivement.
 *
 * Seuils (ajustables) :
 *   {@value #MAX_PACKETS_PER_SECOND} packets/s   — seuil normal
 *   {@value #WARN_PACKETS_PER_SECOND} packets/s  — seuil de warning (log sans bannir)
 */
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    public  static final int  MAX_PACKETS_PER_SECOND  = 30;
    private static final int  WARN_PACKETS_PER_SECOND = 20;
    private static final int  BAN_DURATION_SEC        = 5;
    private static final int  MAX_CONSECUTIVE_BANS    = 3;

    // ── État par session ──────────────────────────────────────────────────────

    static final class SessionState {
        final AtomicInteger packetCount     = new AtomicInteger(0);
        final AtomicLong    windowStart     = new AtomicLong(System.currentTimeMillis());
        volatile long       bannedUntil     = 0;
        final AtomicInteger consecutiveBans = new AtomicInteger(0);
    }

    /** sessionId → état */
    private static final Map<Long, SessionState> states = new ConcurrentHashMap<>();

    // ── Nettoyage périodique ──────────────────────────────────────────────────

    private static final ScheduledExecutorService cleaner =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-cleaner");
            t.setDaemon(true);
            return t;
        });

    static {
        // Purge toutes les 60 s des sessions inactives
        cleaner.scheduleAtFixedRate(() -> {
            long cutoff = System.currentTimeMillis() - 60_000L;
            states.entrySet().removeIf(e -> e.getValue().windowStart.get() < cutoff
                && e.getValue().bannedUntil < System.currentTimeMillis());
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Appelé pour chaque packet reçu.
     *
     * @param sessionId Identifiant MINA de la session
     * @return {@code true} si le packet peut être traité, {@code false} si bloqué
     */
    public static boolean allow(long sessionId) {
        SessionState s = states.computeIfAbsent(sessionId, k -> new SessionState());

        // Vérifie si ban actif
        long now = System.currentTimeMillis();
        if(s.bannedUntil > now) {
            logger.debug("RateLimiter : session {} bloquée ({} ms restant)",
                sessionId, s.bannedUntil - now);
            return false;
        }

        // Réinitialise la fenêtre si on est dans une nouvelle seconde
        if(now - s.windowStart.get() >= 1000) {
            s.packetCount.set(0);
            s.windowStart.set(now);
        }

        int count = s.packetCount.incrementAndGet();

        if(count > MAX_PACKETS_PER_SECOND) {
            int bans = s.consecutiveBans.incrementAndGet();
            s.bannedUntil = now + BAN_DURATION_SEC * 1000L;

            if(bans >= MAX_CONSECUTIVE_BANS) {
                logger.warn("RateLimiter : session {} — {} bans consécutifs → déconnexion forcée",
                    sessionId, bans);
                // Le caller doit fermer la session (on retourne false, il appellera closeNow)
                states.remove(sessionId);
                return false; // signal spécial : déconnecter
            }

            logger.warn("RateLimiter : session {} flood détecté ({} pkt/s) — banni {}s [ban #{}/{}]",
                new Object[] { sessionId, count, BAN_DURATION_SEC, bans, MAX_CONSECUTIVE_BANS});
            return false;
        }

        if(count > WARN_PACKETS_PER_SECOND) {
            logger.debug("RateLimiter : session {} — {} pkt/s (seuil d'alerte)",
                sessionId, count);
        }

        return true;
    }

    /**
     * Enregistre le bon comportement d'une session (réinitialise les bans consécutifs).
     * À appeler après une période calme significative.
     */
    public static void reset(long sessionId) {
        SessionState s = states.get(sessionId);
        if(s != null) s.consecutiveBans.set(0);
    }

    /** Retire la session du registre (à la déconnexion). */
    public static void remove(long sessionId) {
        states.remove(sessionId);
    }

    /**
     * Indique si une session est encore dans le registre.
     * Retourne {@code false} si elle a été purgée après 3 bans consécutifs
     * (signal pour {@code Game} de fermer la connexion réseau).
     */
    public static boolean isTracked(long sessionId) {
        return states.containsKey(sessionId);
    }

    /** Nombre de sessions actuellement suivies. */
    public static int size() { return states.size(); }
}
