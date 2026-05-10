package org.dofus.objects.actors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moteur d'apprentissage des bots — renforcement positif simple.
 *
 * Principe :
 *   1. Quand un bot parle, son message est enregistré sur la map avec un timestamp.
 *   2. Si un joueur répond dans la fenêtre de réaction (20 s), le message reçoit +1 réaction.
 *   3. Les messages avec un bon ratio réactions/utilisations montent dans le pool pondéré.
 *   4. Le pool est persisté dans {@value #MEMORY_FILE} et rechargé au démarrage.
 *
 * Résultat : les bots apprennent collectivement quels messages attirent des réponses.
 * Les réponses générées par OpenAI qui déclenchent une interaction entrent aussi dans le pool.
 *
 * Formule de score : (réactions + 1) / (utilisations + 2)  [lissage de Laplace]
 */
public class BotLearning {

    private static final Logger logger = LoggerFactory.getLogger(BotLearning.class);

    /** Fichier CSV de persistance : PERSONALITY|uses|reactions|phrase */
    private static final String MEMORY_FILE     = "bot_memory.csv";

    /** Fenêtre de réaction : si un joueur parle dans ce délai après un bot, +1 réaction. */
    private static final long   REACTION_WINDOW = 20_000L;

    /** Taille max du pool par personnalité. Les phrases les plus faibles sont éliminées. */
    private static final int    MAX_PHRASES     = 100;

    /**
     * Probabilité d'utiliser une phrase apprise (vs phrase statique de BotConversation).
     * Augmente progressivement avec la taille du pool (voir {@link #pickLearned}).
     */
    private static final double BASE_USE_RATE   = 0.35;

    /** Nombre minimum d'utilisations avant qu'une phrase soit considérée fiable. */
    private static final int    MIN_USES        = 3;

    // ── Modèle interne ────────────────────────────────────────────────────────

    static final class LearnedPhrase {
        final String phrase;
        volatile int uses;
        volatile int reactions;

        LearnedPhrase(String phrase, int uses, int reactions) {
            this.phrase    = phrase;
            this.uses      = uses;
            this.reactions = reactions;
        }

        /** Score avec lissage de Laplace — stable même avec peu d'observations. */
        double score() {
            return (reactions + 1.0) / (uses + 2.0);
        }

        @Override
        public String toString() {
            return String.format("[%.2f u=%d r=%d] %s", score(), uses, reactions,
                phrase.length() > 40 ? phrase.substring(0, 40) + "…" : phrase);
        }
    }

    /** Pool de phrases apprises — une liste par personnalité, thread-safe via synchronized. */
    private static final Map<BotPersonality, List<LearnedPhrase>> pool = new ConcurrentHashMap<>();

    // ── Tracking des messages récents par map ─────────────────────────────────

    static final class RecentMessage {
        final int            botId;
        final BotPersonality personality;
        final String         phrase;
        final long           timestamp;

        RecentMessage(int botId, BotPersonality p, String phrase) {
            this.botId       = botId;
            this.personality = p;
            this.phrase      = phrase;
            this.timestamp   = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REACTION_WINDOW;
        }
    }

    /** mapId → file FIFO des messages bots récents (nettoyée à chaque accès). */
    private static final Map<Integer, Deque<RecentMessage>> recentByMap = new ConcurrentHashMap<>();

    // ── Planificateur de sauvegarde ───────────────────────────────────────────

    private static final ScheduledExecutorService saver =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bot-learning");
            t.setDaemon(true);
            return t;
        });

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Initialise les pools et charge la mémoire persistée.
     * À appeler une seule fois au démarrage, avant {@link BotAI#spawnAll()}.
     */
    public static void init() {
        for(BotPersonality p : BotPersonality.values()) {
            pool.put(p, Collections.synchronizedList(new ArrayList<LearnedPhrase>()));
        }
        load();
        // Sauvegarde automatique toutes les 5 minutes
        saver.scheduleAtFixedRate(BotLearning::save, 5, 5, TimeUnit.MINUTES);

        int total = 0;
        for(List<LearnedPhrase> l : pool.values()) total += l.size();
        logger.info("BotLearning initialisé — {} phrases chargées en mémoire", total);
    }

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Appelé chaque fois qu'un bot parle sur une map.
     * Enregistre le message pour le renforcement potentiel et met à jour le compteur uses.
     *
     * @param botId       ID du bot émetteur
     * @param personality Personnalité du bot
     * @param phrase      Message envoyé
     * @param mapId       Identifiant de la map courante
     */
    public static void onBotSpoke(int botId, BotPersonality personality, String phrase, int mapId) {
        List<LearnedPhrase> pPool = pool.get(personality);
        if(pPool == null) return;

        synchronized(pPool) {
            boolean found = false;
            for(LearnedPhrase lp : pPool) {
                if(lp.phrase.equals(phrase)) {
                    lp.uses++;
                    found = true;
                    break;
                }
            }
            if(!found) {
                // Nouvelle phrase : entrée dans le pool
                if(pPool.size() >= MAX_PHRASES) pruneLowest(pPool);
                pPool.add(new LearnedPhrase(phrase, 1, 0));
            }
        }
        registerRecent(mapId, botId, personality, phrase);
    }

    /**
     * Appelé chaque fois qu'un joueur parle sur une map (canal général).
     * Renforce toutes les phrases de bots récentes encore dans la fenêtre de réaction.
     *
     * @param mapId Identifiant de la map
     */
    public static void onPlayerSpoke(int mapId) {
        Deque<RecentMessage> deque = recentByMap.get(mapId);
        if(deque == null || deque.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Copie pour éviter ConcurrentModificationException
        List<RecentMessage> snapshot = new ArrayList<RecentMessage>(deque);
        for(RecentMessage rm : snapshot) {
            if(now - rm.timestamp <= REACTION_WINDOW) {
                reinforce(rm.personality, rm.phrase);
            }
        }
        // Nettoyage des entrées expirées
        deque.removeIf(RecentMessage::isExpired);
    }

    /**
     * Propose une phrase apprise aléatoirement (pondérée par score).
     * Retourne {@code null} si le pool est vide, pas assez mature, ou si le tirage
     * aléatoire décide d'utiliser le pool statique.
     *
     * @param personality Personnalité du bot
     * @return Phrase apprise ou {@code null}
     */
    public static String pickLearned(BotPersonality personality) {
        List<LearnedPhrase> pPool = pool.get(personality);
        if(pPool == null) return null;

        List<LearnedPhrase> candidates;
        synchronized(pPool) {
            candidates = new ArrayList<LearnedPhrase>();
            for(LearnedPhrase lp : pPool) {
                if(lp.uses >= MIN_USES) candidates.add(lp);
            }
        }
        if(candidates.isEmpty()) return null;

        // Taux d'utilisation dynamique : augmente avec la maturité du pool
        double useRate = Math.min(0.70, BASE_USE_RATE + candidates.size() * 0.005);
        if(Math.random() > useRate) return null;

        // Tirage pondéré par le score
        double totalWeight = 0;
        for(LearnedPhrase lp : candidates) totalWeight += lp.score();

        double pick = Math.random() * totalWeight;
        double acc  = 0;
        for(LearnedPhrase lp : candidates) {
            acc += lp.score();
            if(acc >= pick) return lp.phrase;
        }
        return candidates.get(candidates.size() - 1).phrase;
    }

    /**
     * Soumet une phrase externe dans le pool (ex : réponse générée par OpenAI).
     * La phrase sera renforcée naturellement si des joueurs réagissent.
     *
     * @param personality Personnalité concernée
     * @param phrase      Phrase à intégrer
     */
    public static void submitPhrase(BotPersonality personality, String phrase) {
        if(phrase == null || phrase.trim().isEmpty()) return;
        List<LearnedPhrase> pPool = pool.get(personality);
        if(pPool == null) return;

        synchronized(pPool) {
            for(LearnedPhrase lp : pPool) {
                if(lp.phrase.equals(phrase)) return; // déjà présente
            }
            if(pPool.size() >= MAX_PHRASES) pruneLowest(pPool);
            pPool.add(new LearnedPhrase(phrase.trim(), 0, 0));
            logger.debug("BotLearning : nouvelle phrase [{}] soumise au pool «{}»",
                personality, phrase.length() > 50 ? phrase.substring(0, 50) + "…" : phrase);
        }
    }

    /**
     * Retourne les statistiques d'apprentissage (pour logs/debug).
     *
     * @return Chaîne multi-ligne résumant chaque pool
     */
    public static String getStats() {
        StringBuilder sb = new StringBuilder("=== BotLearning Stats ===\n");
        for(Map.Entry<BotPersonality, List<LearnedPhrase>> e : pool.entrySet()) {
            List<LearnedPhrase> pPool = e.getValue();
            int size, mature;
            double avgScore;
            synchronized(pPool) {
                size   = pPool.size();
                mature = 0;
                avgScore = 0;
                for(LearnedPhrase lp : pPool) {
                    if(lp.uses >= MIN_USES) { mature++; avgScore += lp.score(); }
                }
                if(mature > 0) avgScore /= mature;
            }
            sb.append(String.format("  %-10s : %d phrases (%d matures, score moy=%.2f)\n",
                e.getKey(), size, mature, avgScore));
        }
        return sb.toString();
    }

    // ── Persistance ───────────────────────────────────────────────────────────

    /** Sauvegarde le pool complet dans {@value #MEMORY_FILE}. Thread-safe. */
    public static synchronized void save() {
        try(PrintWriter pw = new PrintWriter(new FileWriter(MEMORY_FILE, false))) {
            pw.println("# BotLearning memory — format: PERSONALITY|uses|reactions|phrase");
            for(Map.Entry<BotPersonality, List<LearnedPhrase>> e : pool.entrySet()) {
                synchronized(e.getValue()) {
                    for(LearnedPhrase lp : e.getValue()) {
                        // Échappe les | dans la phrase pour ne pas casser le parsing
                        String safe = lp.phrase.replace("\\", "\\\\").replace("|", "\\|");
                        pw.println(e.getKey().name() + "|" + lp.uses + "|" + lp.reactions + "|" + safe);
                    }
                }
            }
            logger.debug("BotLearning : pool sauvegardé dans {}", MEMORY_FILE);
        } catch(IOException ex) {
            logger.warn("BotLearning : échec de sauvegarde : {}", ex.getMessage());
        }
    }

    /** Charge le pool depuis {@value #MEMORY_FILE}. Appelé une seule fois dans {@link #init()}. */
    private static void load() {
        File f = new File(MEMORY_FILE);
        if(!f.exists()) {
            logger.info("BotLearning : {} introuvable, démarrage à zéro", MEMORY_FILE);
            return;
        }

        int count = 0;
        try(BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while((line = br.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#")) continue;

                // Format : PERSONALITY|uses|reactions|phrase
                int sep1 = line.indexOf('|');
                int sep2 = sep1 >= 0 ? line.indexOf('|', sep1 + 1) : -1;
                int sep3 = sep2 >= 0 ? line.indexOf('|', sep2 + 1) : -1;
                if(sep3 < 0) { logger.debug("BotLearning : ligne ignorée : {}", line); continue; }

                try {
                    BotPersonality p  = BotPersonality.valueOf(line.substring(0, sep1));
                    int  uses         = Integer.parseInt(line.substring(sep1 + 1, sep2));
                    int  reactions    = Integer.parseInt(line.substring(sep2 + 1, sep3));
                    String phrase     = line.substring(sep3 + 1)
                                           .replace("\\|", "|")
                                           .replace("\\\\", "\\");
                    List<LearnedPhrase> pPool = pool.get(p);
                    if(pPool != null) {
                        pPool.add(new LearnedPhrase(phrase, uses, reactions));
                        count++;
                    }
                } catch(Exception ex) {
                    logger.debug("BotLearning : ligne invalide ignorée : {}", line);
                }
            }
        } catch(IOException ex) {
            logger.warn("BotLearning : échec de lecture : {}", ex.getMessage());
        }
        logger.info("BotLearning : {} phrases chargées depuis {}", count, MEMORY_FILE);
    }

    /** Sauvegarde finale + arrêt du planificateur. Appelé dans {@code Main.stop()}. */
    public static void shutdown() {
        save();
        saver.shutdown();
        logger.info("BotLearning : sauvegarde finale effectuée. {}", getStats());
    }

    // ── Utilitaires privés ────────────────────────────────────────────────────

    private static void registerRecent(int mapId, int botId, BotPersonality p, String phrase) {
        Deque<RecentMessage> deque = recentByMap.computeIfAbsent(
            mapId, k -> new LinkedList<RecentMessage>());
        // Purge des entrées expirées avant d'ajouter
        synchronized(deque) {
            deque.removeIf(RecentMessage::isExpired);
            deque.addLast(new RecentMessage(botId, p, phrase));
        }
    }

    private static void reinforce(BotPersonality personality, String phrase) {
        List<LearnedPhrase> pPool = pool.get(personality);
        if(pPool == null) return;
        synchronized(pPool) {
            for(LearnedPhrase lp : pPool) {
                if(lp.phrase.equals(phrase)) {
                    lp.reactions++;
                    /*logger.debug("BotLearning : +1 réaction [{}] «{}» score={}" new Object {,
                        personality,
                        phrase.length() > 40 ? phrase.substring(0, 40) + "…" : phrase,
                        String.format("%.2f", lp.score()))};*/
                    return;
                }
            }
        }
    }

    /** Supprime la phrase ayant le score le plus bas dans le pool (pool plein). */
    private static void pruneLowest(List<LearnedPhrase> pPool) {
        if(pPool.isEmpty()) return;
        LearnedPhrase worst = null;
        for(LearnedPhrase lp : pPool) {
            if(lp.uses >= MIN_USES && (worst == null || lp.score() < worst.score())) {
                worst = lp;
            }
        }
        // Si aucune phrase mature, supprimer la plus ancienne (premier élément)
        if(worst == null && !pPool.isEmpty()) worst = pPool.get(0);
        if(worst != null) {
            pPool.remove(worst);
            logger.debug("BotLearning : phrase prunée : {}", worst);
        }
    }
}
