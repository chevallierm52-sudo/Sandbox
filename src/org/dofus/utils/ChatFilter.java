package org.dofus.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtre anti-spam de chat Dofus 1.29.
 *
 * Protège contre :
 *   1. Flood de messages identiques (même texte répété en moins de X secondes)
 *   2. Flood de messages rapides (N messages dans la fenêtre de temps)
 *   3. Majuscules excessives (CAPS abuse)
 *   4. Répétition de caractères (aaaaaaa)
 *
 * Les seuils sont volontairement permissifs pour ne pas gêner le jeu normal.
 * À intégrer dans {@code BasicParser.channelsMessage()} avant la diffusion.
 *
 * Usage :
 *   {@code if (!ChatFilter.allow(characterId, message)) { session.write("BN"); return; }}
 */
public class ChatFilter {

    private static final Logger logger = LoggerFactory.getLogger(ChatFilter.class);

    // ── Seuils ────────────────────────────────────────────────────────────────

    /** Délai minimum entre deux messages identiques (ms). */
    private static final long   DUPLICATE_COOLDOWN_MS = 4_000L;

    /** Nombre max de messages dans la fenêtre de flood. */
    private static final int    MAX_MESSAGES_IN_WINDOW = 5;

    /** Durée de la fenêtre de flood (ms). */
    private static final long   FLOOD_WINDOW_MS = 3_000L;

    /** Pourcentage maximum de majuscules dans un message (0-100). */
    private static final int    MAX_CAPS_PERCENT = 70;

    /** Longueur minimum du message pour appliquer le filtre majuscules. */
    private static final int    MIN_LEN_FOR_CAPS = 6;

    /** Nombre max de répétitions du même caractère consécutif. */
    private static final int    MAX_CHAR_REPEAT = 5;

    // ── État par personnage ───────────────────────────────────────────────────

    private static class PlayerState {
        String lastMessage    = "";
        long   lastMessageAt  = 0;
        int    msgCount       = 0;
        long   windowStart    = 0;
    }

    private static final Map<Integer, PlayerState> states = new ConcurrentHashMap<>();

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Vérifie si un message peut être envoyé.
     *
     * @param characterId ID du personnage
     * @param message     Message à envoyer
     * @return {@code true} si autorisé, {@code false} si bloqué
     */
    public static boolean allow(int characterId, String message) {
        if(message == null || message.isEmpty()) return false;

        PlayerState state = states.computeIfAbsent(characterId, k -> new PlayerState());
        long now = System.currentTimeMillis();

        // 1. Doublon récent
        if(message.equalsIgnoreCase(state.lastMessage) &&
           (now - state.lastMessageAt) < DUPLICATE_COOLDOWN_MS) {
            logger.debug("ChatFilter [{}] : doublon bloqué", characterId);
            return false;
        }

        // 2. Flood rapide
        if(now - state.windowStart > FLOOD_WINDOW_MS) {
            state.windowStart = now;
            state.msgCount    = 0;
        }
        state.msgCount++;
        if(state.msgCount > MAX_MESSAGES_IN_WINDOW) {
            logger.debug("ChatFilter [{}] : flood bloqué ({} msg/{}ms)", new Object[] { characterId, state.msgCount, FLOOD_WINDOW_MS});
            return false;
        }

        // 3. Trop de majuscules
        if(message.length() >= MIN_LEN_FOR_CAPS && capsPercent(message) > MAX_CAPS_PERCENT) {
            logger.debug("ChatFilter [{}] : caps abusifs bloqués", characterId);
            return false;
        }

        // 4. Répétition de caractères
        if(hasExcessiveRepeat(message)) {
            logger.debug("ChatFilter [{}] : répétition de caractères bloquée", characterId);
            return false;
        }

        state.lastMessage   = message;
        state.lastMessageAt = now;
        return true;
    }

    /** Libère l'état d'un joueur (déconnexion). */
    public static void remove(int characterId) {
        states.remove(characterId);
    }

    // ── Utilitaires privés ────────────────────────────────────────────────────

    private static int capsPercent(String msg) {
        int caps = 0, letters = 0;
        for(char c : msg.toCharArray()) {
            if(Character.isLetter(c)) {
                letters++;
                if(Character.isUpperCase(c)) caps++;
            }
        }
        return (letters == 0) ? 0 : caps * 100 / letters;
    }

    private static boolean hasExcessiveRepeat(String msg) {
        if(msg.length() < MAX_CHAR_REPEAT + 1) return false;
        int repeat = 1;
        for(int i = 1; i < msg.length(); i++) {
            if(msg.charAt(i) == msg.charAt(i - 1)) {
                repeat++;
                if(repeat > MAX_CHAR_REPEAT) return true;
            } else {
                repeat = 1;
            }
        }
        return false;
    }
}
