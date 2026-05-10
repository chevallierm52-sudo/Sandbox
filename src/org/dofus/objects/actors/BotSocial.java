package org.dofus.objects.actors;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Système social entre bots.
 *
 * - Groupes d'amis : chaque bot peut avoir des amis (autres bots qu'il connaît).
 * - Suivi de map : quand un ami change de map, les autres peuvent le suivre.
 * - Notifications : annonces en chat de map lors d'un changement.
 *
 * Les groupes sont enregistrés au démarrage via {@link #addFriendship(int, int)}.
 */
public class BotSocial {

    private static final Logger logger = LoggerFactory.getLogger(BotSocial.class);

    /** botId → ensemble des botIds amis */
    private static final Map<Integer, Set<Integer>> friendships = new ConcurrentHashMap<>();

    /** botId → mapId courant (mis à jour à chaque changement de map) */
    private static final Map<Integer, Integer> botMapCache = new ConcurrentHashMap<>();

    // ── Enregistrement des amitiés ────────────────────────────────────────────

    /** Crée une amitié bidirectionnelle entre deux bots. */
    public static void addFriendship(int botId1, int botId2) {
        friendships.computeIfAbsent(botId1, k -> ConcurrentHashMap.newKeySet()).add(botId2);
        friendships.computeIfAbsent(botId2, k -> ConcurrentHashMap.newKeySet()).add(botId1);
    }

    /** Crée un groupe d'amis (toutes les paires possibles dans le groupe). */
    public static void addFriendGroup(int... botIds) {
        for(int i = 0; i < botIds.length; i++)
            for(int j = i + 1; j < botIds.length; j++)
                addFriendship(botIds[i], botIds[j]);
    }

    public static Set<Integer> getFriends(int botId) {
        return friendships.getOrDefault(botId, Collections.emptySet());
    }

    // ── Gestion des positions ─────────────────────────────────────────────────

    public static void updateLocation(int botId, int mapId) {
        botMapCache.put(botId, mapId);
    }

    public static int getLocation(int botId) {
        return botMapCache.getOrDefault(botId, -1);
    }

    // ── Logique de suivi ──────────────────────────────────────────────────────

    /**
     * Appelé quand un bot vient de changer de map.
     * Notifie ses amis présents sur l'ancienne map : ils peuvent décider de suivre.
     *
     * @param movingBot  Le bot qui vient de partir
     * @param fromMapId  L'ancienne map
     * @param toMap      La nouvelle MapTemplate
     * @param trigger    Le trigger utilisé (pour connaître la cellule d'arrivée)
     */
    public static void onBotChangedMap(Characters movingBot, int fromMapId,
                                       org.dofus.objects.maps.MapTemplate toMap,
                                       org.dofus.objects.maps.MapTemplate.TriggerTemplate trigger,
                                       BotPersonality movingPersonality) {

        updateLocation(movingBot.getId(), toMap.getId());
        Set<Integer> friends = getFriends(movingBot.getId());
        if(friends.isEmpty()) return;

        for(int friendId : friends) {
            if(getLocation(friendId) != fromMapId) continue; // pas sur la même map

            Characters friend = WorldData.getCharacterById(friendId);
            if(friend == null) continue;

            BotPersonality friendPersonality = BotAI.getPersonality(friendId);
            if(friendPersonality == null) continue;

            // Décision de suivi basée sur la personnalité de l'ami
            if(Math.random() < friendPersonality.getFollowWeight()) {
                scheduleFollow(friend, movingBot.getName(), toMap, trigger, friendPersonality);
            }
        }
    }

    // ── Exécution du suivi ────────────────────────────────────────────────────

    private static void scheduleFollow(Characters follower, String leaderName,
                                       org.dofus.objects.maps.MapTemplate toMap,
                                       org.dofus.objects.maps.MapTemplate.TriggerTemplate trigger,
                                       BotPersonality personality) {

        long delay = 2 + (long)(Math.random() * 4); // 2-6 secondes de délai

        BotBehavior.schedule(() -> {
            try {
                // Annonce en chat de map
                String announce = BotConversation.getFollowAnnounce(leaderName);
                broadcastToMap(follower, "cMK*|" + follower.getId() + "|" + follower.getName() + "|" + announce);

                // Déclenche le changement de map via BotBehavior
                BotBehavior.performMapChange(follower, toMap, trigger.getNextCellId());
                updateLocation(follower.getId(), toMap.getId());

                logger.debug("Bot {} follows {} to map {}", new Object[] { follower.getName(), leaderName, toMap.getId()});
            } catch(Exception e) {
                logger.warn("Bot {} follow error: {}", follower.getName(), e.getMessage());
            }
        }, delay, java.util.concurrent.TimeUnit.SECONDS);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static void broadcastToMap(Characters bot, String packet) {
        for(Characters actor : new java.util.ArrayList<>(bot.getCurrentMap().getActors().values())) {
            if(actor == bot) continue;
            IoSession session = WorldData.getSessionByAccount().get(actor.getOwner());
            if(session != null && session.isConnected())
                session.write(packet);
        }
    }
}
