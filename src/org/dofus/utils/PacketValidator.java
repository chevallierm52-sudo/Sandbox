package org.dofus.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validation centralisée des paquets entrants — protection anti-triche / anti-crash.
 *
 * Chaque méthode retourne {@code true} si le paquet est valide, {@code false} sinon.
 * En cas d'échec, un log WARN est émis avec l'ID de session pour traçabilité.
 *
 * Règles appliquées :
 *   - Longueur maximale de paquet global
 *   - Cellule de déplacement dans [0, MAX_CELL]
 *   - Message de chat : longueur et contenu de base
 *   - Bonus de stat : identifiant et valeur dans la plage autorisée
 *   - Dialogue : actorId au format attendu
 *   - Paquet de combat GA : longueur minimale et format
 */
public class PacketValidator {

    private static final Logger logger = LoggerFactory.getLogger(PacketValidator.class);

    /** Longueur maximale d'un paquet brut (avant parsing). */
    public static final int MAX_PACKET_LENGTH  = 512;

    /** Plus grand identifiant de cellule Dofus 1.29 (cartes 33×17 = 560 cells, index 0-559). */
    public static final int MAX_CELL_ID        = 559;

    /** Longueur maximale d'un message de chat (canal général / privé). */
    public static final int MAX_CHAT_LENGTH    = 255;

    /** Valeur maximale acceptable pour un paquet de boost de stat (sanity check). */
    private static final int MAX_STAT_BOOST    = 20_000;

    /** IDs de stat valides pour BoostParser (caractéristiques Dofus 1.29). */
    private static final int[] VALID_STAT_IDS  = { 10, 11, 12, 13, 14, 15 };

    // ── Validations publiques ─────────────────────────────────────────────────

    /**
     * Vérifie qu'un paquet brut n'est pas null, vide, ou trop long.
     *
     * @param sessionId Identifiant de session MINA (pour les logs)
     * @param packet    Chaîne reçue
     * @return {@code true} si valide
     */
    public static boolean validateRaw(long sessionId, String packet) {
        if(packet == null || packet.isEmpty()) {
            logger.warn("[{}] paquet null ou vide", sessionId);
            return false;
        }
        if(packet.length() > MAX_PACKET_LENGTH) {
            logger.warn("[{}] paquet trop long ({} > {})", new Object[] { sessionId, packet.length(), MAX_PACKET_LENGTH});
            return false;
        }
        return true;
    }

    /**
     * Vérifie qu'un identifiant de cellule est dans la plage [0, MAX_CELL_ID].
     *
     * @param sessionId Identifiant de session (logs)
     * @param cellId    Cellule à valider
     * @return {@code true} si valide
     */
    public static boolean validateCellId(long sessionId, int cellId) {
        if(cellId < 0 || cellId > MAX_CELL_ID) {
            logger.warn("[{}] cellId invalide : {} (plage 0-{})", new Object[] { sessionId, cellId, MAX_CELL_ID});
            return false;
        }
        return true;
    }

    /**
     * Vérifie qu'un message de chat n'est pas vide et ne dépasse pas {@value #MAX_CHAT_LENGTH} caractères.
     * Détecte également les caractères de contrôle qui pourraient polluer le protocole.
     *
     * @param sessionId Identifiant de session (logs)
     * @param message   Message à valider
     * @return {@code true} si valide
     */
    public static boolean validateChatMessage(long sessionId, String message) {
        if(message == null || message.trim().isEmpty()) {
            logger.warn("[{}] message de chat vide", sessionId);
            return false;
        }
        if(message.length() > MAX_CHAT_LENGTH) {
            logger.warn("[{}] message de chat trop long ({} > {})",
            	new Object[] { sessionId, message.length(), MAX_CHAT_LENGTH});
            return false;
        }
        // Rejet des caractères de contrôle qui coupent le protocole ligne-par-ligne
        for(int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if(c == '\0' || c == '\n' || c == '\r') {
                logger.warn("[{}] message de chat contient un caractère de contrôle (\\x{:02X})",
                    sessionId, (int) c);
                return false;
            }
        }
        return true;
    }

    /**
     * Vérifie qu'un identifiant de stat (BoostParser) est dans la liste autorisée
     * et que la quantité de points investis ne dépasse pas une borne de sanité.
     *
     * @param sessionId Identifiant de session (logs)
     * @param statId    Identifiant de caractéristique
     * @param amount    Quantité de points à investir
     * @return {@code true} si valide
     */
    public static boolean validateStatBoost(long sessionId, int statId, int amount) {
        if(amount <= 0 || amount > MAX_STAT_BOOST) {
            logger.warn("[{}] boost de stat invalide : statId={} amount={}", new Object[] { sessionId, statId, amount});
            return false;
        }
        for(int id : VALID_STAT_IDS) {
            if(id == statId) return true;
        }
        logger.warn("[{}] statId inconnu : {}", sessionId, statId);
        return false;
    }

    /**
     * Vérifie qu'un identifiant d'acteur de dialogue est cohérent.
     * Les PNJ ont un actorId ≥ 100 000 (offset NPC = spawnId + 100_000).
     *
     * @param sessionId Identifiant de session (logs)
     * @param actorId   Identifiant de l'acteur du dialogue
     * @return {@code true} si valide
     */
    public static boolean validateDialogActor(long sessionId, int actorId) {
        if(actorId < 100_000) {
            logger.warn("[{}] actorId de dialogue suspect : {} (attendu ≥ 100000)", sessionId, actorId);
            return false;
        }
        return true;
    }

    /**
     * Vérifie la longueur minimale d'un paquet GA (action de combat).
     * Le format minimal est : {@code GA}{actionType};{sourceFighterId};... → 5 caractères.
     *
     * @param sessionId Identifiant de session (logs)
     * @param packet    Paquet GA brut
     * @return {@code true} si le paquet a la longueur minimale attendue
     */
    public static boolean validateFightAction(long sessionId, String packet) {
        if(packet == null || packet.length() < 5) {
            logger.warn("[{}] paquet GA trop court : '{}'", sessionId, packet);
            return false;
        }
        return true;
    }

    /**
     * Vérifie qu'une valeur de kamas ne dépasse pas la limite du jeu (1 milliard).
     *
     * @param sessionId Identifiant de session (logs)
     * @param kamas     Valeur à valider
     * @return {@code true} si valide
     */
    public static boolean validateKamas(long sessionId, long kamas) {
        if(kamas < 0 || kamas > 1_000_000_000L) {
            logger.warn("[{}] valeur de kamas hors limites : {}", sessionId, kamas);
            return false;
        }
        return true;
    }

    /**
     * Vérifie qu'un identifiant d'item (template ou instance) est positif.
     *
     * @param sessionId Identifiant de session (logs)
     * @param itemId    Identifiant d'item
     * @return {@code true} si valide
     */
    public static boolean validateItemId(long sessionId, int itemId) {
        if(itemId <= 0) {
            logger.warn("[{}] itemId invalide : {}", sessionId, itemId);
            return false;
        }
        return true;
    }
}
