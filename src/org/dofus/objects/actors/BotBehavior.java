package org.dofus.objects.actors;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.MapsData;
import org.dofus.network.game.protocols.GProtocol;
import org.dofus.objects.WorldData;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;
import org.dofus.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moteur comportemental des bots — FSM à personnalité.
 *
 * États :
 *   WANDERING  — déplacement aléatoire sur la map courante
 *   EXPLORING  — déplacement actif vers un bord de map (changement de map)
 *   FOLLOWING  — suit un ami vers sa nouvelle map (géré par BotSocial)
 *
 * La décision d'état est prise à chaque tick selon les poids de BotPersonality.
 */
public class BotBehavior {

    private static final Logger logger = LoggerFactory.getLogger(BotBehavior.class);

    private static final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "bot-ai");
            t.setDaemon(true);
            return t;
        });

    // ── Directions isométriques ───────────────────────────────────────────────

    // [EOrientation ordinal, cell offset]
    private static final int[][] MOVE_DIRS = {
        { 0,   1 }, // EAST       (+1)
        { 1,  14 }, // SOUTH_EAST (+14, vers le bas = border sud)
        { 4,  -1 }, // WEST       (-1)
        { 5, -14 }, // NORTH_WEST (-14, vers le haut = border nord)
        { 2,  13 }, // SOUTH      (+13)
        { 3, -13 }, // NORTH      (-13)
    };

    // Directions privilégiées pour chercher un bord (SE / NW)
    private static final int[][] EXPLORE_DIRS = {
        { 1,  14 }, // SOUTH_EAST → bord bas-droit
        { 5, -14 }, // NORTH_WEST → bord haut-gauche
    };

    private static final int CELL_MAX = 559;

    /** État FSM courant par bot (botId → état). */
    private static final Map<Integer, BotState> states = new ConcurrentHashMap<>();
    private static final Map<Integer, Short> targetTriggerCells = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> movingUntil = new ConcurrentHashMap<>();

    private enum BotState { WANDERING, EXPLORING }

    // ── Démarrage ─────────────────────────────────────────────────────────────

    public static void start(Characters bot) {
        BotPathMemory.init();
        states.put(bot.getId(), BotState.WANDERING);
        BotSocial.updateLocation(bot.getId(), bot.getCurrentMap().getId());

        long moveDelay = 5  + (long)(Math.random() * 20); // 5-25s
        long talkDelay = 15 + (long)(Math.random() * 45); // 15-60s

        scheduler.schedule(() -> scheduleMoves(bot), moveDelay, TimeUnit.SECONDS);
        scheduler.schedule(() -> scheduleTalk(bot),  talkDelay, TimeUnit.SECONDS);

        logger.debug("Bot {} started (personality={})", bot.getName(), BotAI.getPersonality(bot.getId()));
    }

    // ── Boucle déplacement ────────────────────────────────────────────────────

    private static void scheduleMoves(Characters bot) {
        try {
            if(!isMoving(bot)) {
                tick(bot);
            }
        } catch(Exception e) {
            logger.warn("Bot {} move error: {}", bot.getName(), e.getMessage());
        }
        // Intervalle selon la personnalité : les explorateurs bougent plus vite
        BotPersonality p = BotAI.getPersonality(bot.getId());
        double talkMod = (p != null) ? p.getTalkWeight() : 1.0;
        long next = (long)((8 + Math.random() * 17) / (0.5 + talkMod * 0.5));
        next = Math.max(5, Math.min(30, next)); // bornes 5-30s
        scheduler.schedule(() -> scheduleMoves(bot), next, TimeUnit.SECONDS);
    }

    private static void tick(Characters bot) {
        BotPersonality personality = BotAI.getPersonality(bot.getId());
        if(personality == null) personality = BotPersonality.SOCIAL;

        BotState state = states.getOrDefault(bot.getId(), BotState.WANDERING);
        BotMonsterStrategy.Decision monsterDecision = BotMonsterStrategy.inspectMap(bot, personality);

        // Décision de l'état pour ce tick
        boolean hasTriggers = !bot.getCurrentMap().getTriggers().isEmpty();
        boolean wantsExplore = hasTriggers && Math.random() < personality.getExploreWeight();
        boolean wantsLeaveDanger = hasTriggers && monsterDecision != null && !monsterDecision.isFavorable();

        if(state == BotState.EXPLORING || wantsExplore || wantsLeaveDanger) {
            states.put(bot.getId(), BotState.EXPLORING);
            doTriggerMove(bot, personality);
        } else {
            states.put(bot.getId(), BotState.WANDERING);
            doWanderMove(bot);
        }
    }

    // ── Déplacement aléatoire (WANDERING) ────────────────────────────────────

    private static void doWanderMove(Characters bot) {
        int steps = 1 + (int)(Math.random() * 3);
        int[] dir = MOVE_DIRS[(int)(Math.random() * MOVE_DIRS.length)];
        executeMove(bot, dir[0], dir[1], steps, false);
    }

    // ── Déplacement vers un bord (EXPLORING) ─────────────────────────────────

    private static void doExploreMove(Characters bot, BotPersonality personality) {
        // Choisit une direction de bord
        int[] dir = EXPLORE_DIRS[(int)(Math.random() * EXPLORE_DIRS.length)];
        int steps = 2 + (int)(Math.random() * 2); // 2-3 pas vers le bord
        executeMove(bot, dir[0], dir[1], steps, true);
    }

    // ── Exécution d'un mouvement ──────────────────────────────────────────────

    private static void doTriggerMove(Characters bot, BotPersonality personality) {
        MapTemplate map = bot.getCurrentMap();
        if(map == null || map.getTriggers().isEmpty()) {
            doExploreMove(bot, personality);
            return;
        }

        Short targetCell = targetTriggerCells.get(bot.getId());
        if(targetCell == null || !map.getTriggers().containsKey(targetCell)) {
            TriggerTemplate trigger = BotPathMemory.chooseTrigger(bot, personality);
            if(trigger == null) {
                doExploreMove(bot, personality);
                return;
            }
            targetCell = trigger.getCellId();
            targetTriggerCells.put(bot.getId(), targetCell);
        }

        executeMoveTowardCell(bot, targetCell, true);
    }

    private static void executeMove(Characters bot, int orientOrdinal, int cellOffset,
                                    int steps, boolean exploring) {
        MapTemplate map = bot.getCurrentMap();
        if(map == null) return;

        short currentCell = bot.getCurrentCell();
        short targetCell  = (short)(currentCell + cellOffset * steps);

        if(targetCell < 0 || targetCell > CELL_MAX) {
            if(exploring) {
                // Inverser : bord opposé
                orientOrdinal = (orientOrdinal + 4) % 8;
                cellOffset    = -cellOffset;
                targetCell    = (short)(currentCell + cellOffset * steps);
            }
            if(targetCell < 0 || targetCell > CELL_MAX) return;
        }

        // Construire le chemin : a{startEncoded}{dir}{cell}...
        StringBuilder path = new StringBuilder("a");
        path.append(Cell.encode(currentCell));

        short cell = currentCell;
        int actualSteps = 0;
        for(int i = 0; i < steps; i++) {
            short nextCell = (short)(cell + cellOffset);
            if(!map.isValidActorCell(nextCell, exploring)) break;
            cell = nextCell;
            path.append(StringUtils.HASH.charAt(orientOrdinal));
            path.append(Cell.encode(cell));
            actualSteps++;
        }

        if(actualSteps == 0) return;

        final short        finalCell   = cell;
        final EOrientation finalOrient = EOrientation.valueOf(orientOrdinal);

        broadcastToMap(bot, "GA1;1;" + bot.getId() + ";" + path);

        long animMs = estimateWalkTimeMs(actualSteps);
        movingUntil.put(bot.getId(), System.currentTimeMillis() + animMs);
        scheduler.schedule(() -> {
            bot.setCurrentCell(finalCell);
            bot.setCurrentOrientation(finalOrient);
            movingUntil.remove(bot.getId());

            TriggerTemplate trigger = bot.getCurrentMap().getTriggers().get(finalCell);
            if(trigger != null) {
                botChangeMap(bot, trigger);
            }
        }, animMs, TimeUnit.MILLISECONDS);
    }

    // ── Changement de map ─────────────────────────────────────────────────────

    /**
     * Exécute un changement de map pour un bot (trigger naturel ou suivi d'ami).
     */
    private static void executeMoveTowardCell(Characters bot, short targetCell, boolean exploring) {
        short currentCell = bot.getCurrentCell();
        if(currentCell == targetCell) {
            TriggerTemplate trigger = bot.getCurrentMap().getTriggers().get(targetCell);
            if(trigger != null) botChangeMap(bot, trigger);
            return;
        }

        StringBuilder path = new StringBuilder("a");
        path.append(Cell.encode(currentCell));

        short cell = currentCell;
        EOrientation finalOrient = bot.getCurrentOrientation();
        int steps = 0;
        for(int i = 0; i < 120 && cell != targetCell; i++) {
            int[] step = bestStep(cell, targetCell);
            if(step == null) break;
            short nextCell = (short)(cell + step[1]);
            if(nextCell < 0 || nextCell > CELL_MAX) break;
            if(!bot.getCurrentMap().isValidActorCell(nextCell, true)) break;
            cell = nextCell;
            finalOrient = EOrientation.valueOf(step[0]);
            path.append(StringUtils.HASH.charAt(step[0]));
            path.append(Cell.encode(cell));
            steps++;
        }

        if(steps == 0) {
            targetTriggerCells.remove(bot.getId());
            doWanderMove(bot);
            return;
        }

        final short finalCell = cell;
        final EOrientation orientation = finalOrient;
        broadcastToMap(bot, "GA1;1;" + bot.getId() + ";" + path);

        long animMs = estimateWalkTimeMs(steps);
        movingUntil.put(bot.getId(), System.currentTimeMillis() + animMs);
        scheduler.schedule(() -> {
            bot.setCurrentCell(finalCell);
            bot.setCurrentOrientation(orientation);
            movingUntil.remove(bot.getId());

            TriggerTemplate trigger = bot.getCurrentMap().getTriggers().get(finalCell);
            if(trigger != null) {
                botChangeMap(bot, trigger);
            }
        }, animMs, TimeUnit.MILLISECONDS);
    }

    private static int[] bestStep(short from, short target) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for(int[] dir : MOVE_DIRS) {
            int next = from + dir[1];
            if(next < 0 || next > CELL_MAX) continue;
            int distance = Math.abs(target - next);
            if(distance < bestDistance) {
                bestDistance = distance;
                best = dir;
            }
        }
        return best;
    }

    private static boolean isMoving(Characters bot) {
        Long until = movingUntil.get(bot.getId());
        if(until == null) return false;
        if(System.currentTimeMillis() >= until) {
            movingUntil.remove(bot.getId());
            return false;
        }
        return true;
    }

    private static long estimateWalkTimeMs(int steps) {
        if(steps <= 0) return 0L;
        return Math.max(650L, 320L * steps + 250L);
    }

    static void botChangeMap(Characters bot, TriggerTemplate trigger) {
        MapTemplate nextMap = MapsData.findById(trigger.getNextMap());
        if(nextMap == null) {
            logger.warn("Bot {} : carte {} introuvable", bot.getName(), trigger.getNextMap());
            states.put(bot.getId(), BotState.WANDERING);
            return;
        }

        int fromMapId = bot.getCurrentMap().getId();
        BotPersonality personality = BotAI.getPersonality(bot.getId());
        if(personality == null) personality = BotPersonality.SOCIAL;
        BotPathMemory.observeMove(fromMapId, trigger);
        targetTriggerCells.remove(bot.getId());

        // Annonce si en mode exploration
        if(states.getOrDefault(bot.getId(), BotState.WANDERING) == BotState.EXPLORING
                && personality != null) {
            String msg = BotConversation.getExploreAnnounce(personality);
            broadcastToMap(bot, "cMK*|" + bot.getId() + "|" + bot.getName() + "|" + msg);
        }

        // 1. Retire de l'ancienne map
        broadcastToMap(bot, "GM|-" + bot.getId());
        bot.getCurrentMap().removeActor(bot);

        // 2. Téléport
        bot.setCurrentMap(nextMap);
        bot.setCurrentCell(trigger.getNextCellId());
        states.put(bot.getId(), BotState.WANDERING);

        // 3. Ajoute à la nouvelle map
        nextMap.addActor(bot);
        BotSocial.updateLocation(bot.getId(), nextMap.getId());
        BotMonsterStrategy.inspectMap(bot, personality);

        StringBuilder gm = new StringBuilder("GM|+");
        GProtocol.getCharacterPattern(gm, bot);
        broadcastToMap(bot, gm.toString());

        // Annonce d'arrivée (délai court pour ne pas spammer)
        if(personality != null) {
            String arrive = BotConversation.getArriveMessage(personality);
            scheduler.schedule(() ->
                broadcastToMap(bot, "cMK*|" + bot.getId() + "|" + bot.getName() + "|" + arrive),
                2L, TimeUnit.SECONDS);
        }

        // Notifie les amis pour qu'ils puissent suivre
        BotSocial.onBotChangedMap(bot, fromMapId, nextMap, trigger, personality);

        logger.info("Bot {} : map {} → {} cellule {}",
            new Object[]{ bot.getName(), fromMapId, nextMap.getId(), trigger.getNextCellId() });
    }

    /**
     * Changement de map forcé (ex : suivi d'ami par BotSocial).
     * Pas d'annonce d'exploration, place directement sur la cible.
     */
    public static void performMapChange(Characters bot, MapTemplate targetMap, short targetCell) {
        targetTriggerCells.remove(bot.getId());
        broadcastToMap(bot, "GM|-" + bot.getId());
        bot.getCurrentMap().removeActor(bot);

        bot.setCurrentMap(targetMap);
        bot.setCurrentCell(targetCell);
        states.put(bot.getId(), BotState.WANDERING);

        targetMap.addActor(bot);
        BotSocial.updateLocation(bot.getId(), targetMap.getId());

        StringBuilder gm = new StringBuilder("GM|+");
        GProtocol.getCharacterPattern(gm, bot);
        broadcastToMap(bot, gm.toString());
    }

    // ── Boucle chat ───────────────────────────────────────────────────────────

    private static void scheduleTalk(Characters bot) {
        try {
            talkBot(bot);
        } catch(Exception e) {
            logger.warn("Bot {} talk error: {}", bot.getName(), e.getMessage());
        }
        BotPersonality p = BotAI.getPersonality(bot.getId());
        // Les bots SOCIAL parlent plus souvent (40-90s), les MERCHANT moins (60-150s)
        double talkMod = (p != null) ? p.getTalkWeight() : 0.5;
        long base = (long)(90 / talkMod);
        long next = (long)(base * 0.7 + Math.random() * base * 0.6);
        next = Math.max(30, Math.min(180, next));
        scheduler.schedule(() -> scheduleTalk(bot), next, TimeUnit.SECONDS);
    }

    private static void talkBot(Characters bot) {
        BotPersonality personality = BotAI.getPersonality(bot.getId());
        if(personality == null) return;

        // Priorité : phrase apprise (si pool mature) → sinon phrase statique
        String msg = BotLearning.pickLearned(personality);
        if(msg == null) msg = BotConversation.getGeneralMessage(personality);

        broadcastToMap(bot, "cMK*|" + bot.getId() + "|" + bot.getName() + "|" + msg);
        // Enregistre dans le moteur d'apprentissage pour renforcement potentiel
        BotLearning.onBotSpoke(bot.getId(), personality, msg, bot.getCurrentMap().getId());
        logger.debug("Bot {} says: {}", bot.getName(), msg);
    }

    // ── Réaction à un message de map ─────────────────────────────────────────

    /**
     * Appelé par BotPacketHandler quand un autre joueur/bot parle sur la map.
     * Le bot décide de réagir ou non selon sa personnalité.
     */
    public static void onMapMessage(Characters bot, String senderName, String message) {
        BotPersonality personality = BotAI.getPersonality(bot.getId());
        if(personality == null) return;
        if(senderName.equals(bot.getName())) return; // Ne réagit pas à lui-même

        // Probabilité de réaction
        if(Math.random() > personality.getReplyWeight() * 0.4) return;

        long delay = 3 + (long)(Math.random() * 7); // 3-10 secondes
        scheduler.schedule(() -> {
            BotPersonality p = BotAI.getPersonality(bot.getId());
            if(p == null) return;

            // Utilise l'IA si activée, sinon template
            BotAIService.getResponse(bot, p, "Quelqu'un dit sur la map : \"" + message + "\"",
                response -> broadcastToMap(bot,
                    "cMK*|" + bot.getId() + "|" + bot.getName() + "|" + response));
        }, delay, TimeUnit.SECONDS);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static void broadcastToMap(Characters bot, String packet) {
        for(Characters actor : new ArrayList<>(bot.getCurrentMap().getActors().values())) {
            if(actor == bot) continue;
            IoSession session = WorldData.getSessionByAccount().get(actor.getOwner());
            if(session != null && session.isConnected())
                session.write(packet);
        }
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public static void shutdown() {
        scheduler.shutdown();
    }
}
