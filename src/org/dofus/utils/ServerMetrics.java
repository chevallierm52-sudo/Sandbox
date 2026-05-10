package org.dofus.utils;

import java.util.concurrent.atomic.AtomicLong;

import org.dofus.objects.WorldData;

/**
 * Métriques du serveur Dofus 1.29 Sandbox.
 *
 * Données exposées :
 *   - Uptime (secondes depuis le démarrage)
 *   - Joueurs connectés (depuis WorldData)
 *   - Connexions totales depuis le démarrage
 *   - Paquets reçus / envoyés (TODO : brancher dans Game.java)
 *   - RAM utilisée
 *   - Peak de joueurs simultanés
 *
 * Accès :
 *   {@link AdminParser} (.info)
 *   TODO : endpoint HTTP simple (Jetty ou HttpServer Java intégré)
 */
public class ServerMetrics {

    private static final long START_TIME_MS = System.currentTimeMillis();

    private static final AtomicLong totalConnections = new AtomicLong(0);
    private static final AtomicLong packetsReceived  = new AtomicLong(0);
    private static final AtomicLong packetsSent      = new AtomicLong(0);
    private static volatile int     peakPlayers      = 0;

    // ── Compteurs ─────────────────────────────────────────────────────────────

    /** Appelé à chaque nouvelle connexion authentifiée. */
    public static void onConnect() {
        totalConnections.incrementAndGet();
        int current = WorldData.getCharacters().size();
        if(current > peakPlayers) peakPlayers = current;
    }

    /** Appelé à chaque paquet reçu (depuis Game.java). */
    public static void onPacketReceived() { packetsReceived.incrementAndGet(); }

    /** Appelé à chaque paquet envoyé (depuis Game.java). */
    public static void onPacketSent()     { packetsSent.incrementAndGet(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Uptime en secondes depuis le démarrage du serveur. */
    public static long getUptimeSeconds() {
        return (System.currentTimeMillis() - START_TIME_MS) / 1000;
    }

    /** Uptime formaté (ex : "2h 34m 12s"). */
    public static String getUptimeFormatted() {
        long s = getUptimeSeconds();
        return String.format("%dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** Nombre de joueurs actuellement connectés. */
    public static int getOnlinePlayers() {
        return WorldData.getCharacters().size();
    }

    public static long getTotalConnections() { return totalConnections.get(); }
    public static long getPacketsReceived()  { return packetsReceived.get();  }
    public static long getPacketsSent()      { return packetsSent.get();      }
    public static int  getPeakPlayers()      { return peakPlayers;            }

    /** RAM utilisée en Mo. */
    public static long getUsedMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    /** RAM totale allouée en Mo. */
    public static long getTotalMemoryMb() {
        return Runtime.getRuntime().totalMemory() / (1024 * 1024);
    }

    /** Nombre de combats actifs. */
    public static int getActiveFights() {
        return org.dofus.game.fight.Fight.getActiveFights().size();
    }

    /** Nombre de bots spawned. */
    public static int getBotCount() {
        int bots = 0;
        for(org.dofus.objects.actors.Characters c : WorldData.getCharacters().values()) {
            if(c.getId() < 0) bots++;
        }
        return bots;
    }

    /** Nombre de sessions rate-limited actuellement suivies. */
    public static int getRateLimiterSessions() {
        return RateLimiter.size();
    }

    /** Résumé complet formaté pour les logs ou .info admin. */
    public static String getSummary() {
        return String.format(
            "Uptime=%s | Online=%d (peak=%d) | Fights=%d | Bots=%d | " +
            "Connexions=%d | Recv=%d | Sent=%d | RAM=%d/%d Mo | RateLimit=%d sessions",
            getUptimeFormatted(),
            getOnlinePlayers(), getPeakPlayers(),
            getActiveFights(),
            getBotCount(),
            getTotalConnections(),
            getPacketsReceived(),
            getPacketsSent(),
            getUsedMemoryMb(), getTotalMemoryMb(),
            getRateLimiterSessions()
        );
    }
}
