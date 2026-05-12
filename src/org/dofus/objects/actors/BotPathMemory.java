package org.dofus.objects.actors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.TriggerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memoire simple des chemins decouverts par les bots via les soleils de map.
 *
 * Format CSV : fromMap|triggerCell|toMap|toCell|uses|safe|danger|lastSeen
 */
public class BotPathMemory {

    private static final Logger logger = LoggerFactory.getLogger(BotPathMemory.class);
    private static final String MEMORY_FILE = "bot_paths.csv";

    private static final Map<String, KnownPath> paths = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    public static final class KnownPath {
        final int fromMap;
        final short triggerCell;
        final int toMap;
        final short toCell;
        volatile int uses;
        volatile int safeScore;
        volatile int dangerScore;
        volatile long lastSeen;

        KnownPath(int fromMap, short triggerCell, int toMap, short toCell) {
            this.fromMap = fromMap;
            this.triggerCell = triggerCell;
            this.toMap = toMap;
            this.toCell = toCell;
            this.lastSeen = System.currentTimeMillis();
        }

        double utility(BotPersonality personality) {
            double novelty = uses == 0 ? 8.0 : Math.max(0.0, 4.0 - Math.log(uses + 1));
            double safety = safeScore * 1.4 - dangerScore * 2.0;
            double explorerBonus = personality == BotPersonality.EXPLORER ? novelty * 1.4 : novelty;
            double warriorBonus = personality == BotPersonality.WARRIOR ? safeScore * 0.6 : 0.0;
            double merchantPenalty = personality == BotPersonality.MERCHANT ? dangerScore * 1.2 : 0.0;
            return safety + explorerBonus + warriorBonus - merchantPenalty + Math.random();
        }

        String key() {
            return BotPathMemory.key(fromMap, triggerCell);
        }
    }

    public static void init() {
        if(loaded) return;
        loaded = true;
        load();
    }

    public static TriggerTemplate chooseTrigger(Characters bot, BotPersonality personality) {
        init();
        MapTemplate map = bot.getCurrentMap();
        if(map == null || map.getTriggers().isEmpty()) return null;

        List<TriggerTemplate> candidates = new ArrayList<TriggerTemplate>(map.getTriggers().values());
        candidates.sort(new Comparator<TriggerTemplate>() {
            public int compare(TriggerTemplate a, TriggerTemplate b) {
                double sa = scoreTrigger(map.getId(), a, personality);
                double sb = scoreTrigger(map.getId(), b, personality);
                return Double.compare(sb, sa);
            }
        });
        return candidates.get(0);
    }

    public static void observeMove(int fromMap, TriggerTemplate trigger) {
        if(trigger == null) return;
        init();
        KnownPath path = paths.computeIfAbsent(key(fromMap, trigger.getCellId()),
            k -> new KnownPath(fromMap, trigger.getCellId(), trigger.getNextMap(), trigger.getNextCellId()));
        path.uses++;
        path.lastSeen = System.currentTimeMillis();
    }

    public static void rememberMapAssessment(int mapId, BotMonsterStrategy.Decision decision) {
        init();
        if(decision == null) return;
        Collection<KnownPath> snapshot = new ArrayList<KnownPath>(paths.values());
        for(KnownPath path : snapshot) {
            if(path.toMap != mapId) continue;
            if(decision.isFavorable()) path.safeScore++;
            else path.dangerScore++;
            path.lastSeen = System.currentTimeMillis();
        }
    }

    public static synchronized void save() {
        init();
        try(PrintWriter pw = new PrintWriter(new FileWriter(MEMORY_FILE, false))) {
            pw.println("# fromMap|triggerCell|toMap|toCell|uses|safe|danger|lastSeen");
            for(KnownPath path : paths.values()) {
                pw.println(path.fromMap + "|" + path.triggerCell + "|"
                    + path.toMap + "|" + path.toCell + "|"
                    + path.uses + "|" + path.safeScore + "|"
                    + path.dangerScore + "|" + path.lastSeen);
            }
        } catch(IOException e) {
            logger.warn("BotPathMemory save failed: {}", e.getMessage());
        }
    }

    public static void shutdown() {
        save();
    }

    private static double scoreTrigger(int fromMap, TriggerTemplate trigger, BotPersonality personality) {
        KnownPath known = paths.get(key(fromMap, trigger.getCellId()));
        if(known == null) {
            double base = personality == BotPersonality.EXPLORER ? 10.0 : 4.0;
            return base + Math.random();
        }
        return known.utility(personality);
    }

    private static void load() {
        File file = new File(MEMORY_FILE);
        if(!file.exists()) return;

        int count = 0;
        try(BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line = br.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if(parts.length < 8) continue;
                try {
                    KnownPath path = new KnownPath(
                        Integer.parseInt(parts[0]),
                        Short.parseShort(parts[1]),
                        Integer.parseInt(parts[2]),
                        Short.parseShort(parts[3]));
                    path.uses = Integer.parseInt(parts[4]);
                    path.safeScore = Integer.parseInt(parts[5]);
                    path.dangerScore = Integer.parseInt(parts[6]);
                    path.lastSeen = Long.parseLong(parts[7]);
                    paths.put(path.key(), path);
                    count++;
                } catch(NumberFormatException ignored) { }
            }
        } catch(IOException e) {
            logger.warn("BotPathMemory load failed: {}", e.getMessage());
        }
        logger.info("BotPathMemory: {} chemin(s) charges", count);
    }

    private static String key(int mapId, short triggerCell) {
        return mapId + ":" + triggerCell;
    }
}
