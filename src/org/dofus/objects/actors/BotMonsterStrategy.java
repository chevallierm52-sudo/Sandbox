package org.dofus.objects.actors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.constants.EConstants;
import org.dofus.database.objects.MonstersData;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evalue les groupes de monstres visibles pour guider les bots.
 */
public class BotMonsterStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BotMonsterStrategy.class);
    private static final long ADVICE_TTL_MS = 10L * 60L * 1000L;

    private static final Map<String, Decision> decisions = new ConcurrentHashMap<>();

    public static final class Decision {
        private final int groupId;
        private final boolean favorable;
        private final double confidence;
        private final String reason;
        private final long timestamp;

        Decision(int groupId, boolean favorable, double confidence, String reason) {
            this.groupId = groupId;
            this.favorable = favorable;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }

        public int getGroupId() { return groupId; }
        public boolean isFavorable() { return favorable; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        boolean isFresh() { return System.currentTimeMillis() - timestamp < ADVICE_TTL_MS; }
    }

    public static Decision inspectMap(Characters bot, BotPersonality personality) {
        if(bot == null || bot.getCurrentMap() == null) return null;

        MapTemplate map = bot.getCurrentMap();
        MonstersData.spawnAll(map);
        if(map.getMonsterGroups().isEmpty()) return null;

        List<MonsterGroup> groups = new ArrayList<MonsterGroup>(map.getMonsterGroups().values());
        groups.sort(new Comparator<MonsterGroup>() {
            public int compare(MonsterGroup a, MonsterGroup b) {
                return Double.compare(groupAttractiveness(bot, personality, b),
                                      groupAttractiveness(bot, personality, a));
            }
        });

        MonsterGroup best = groups.get(0);
        String key = key(bot, personality, best);
        Decision cached = decisions.get(key);
        if(cached != null && cached.isFresh()) return cached;

        Decision local = localDecision(bot, personality, best);
        decisions.put(key, local);
        BotPathMemory.rememberMapAssessment(map.getId(), local);

        if(local.getConfidence() < 0.70) {
            BotAIService.getCombatAdvice(bot, personality, summarize(bot, personality, best, local),
                advice -> {
                    Decision refined = parseAdvice(best.getId(), local, advice);
                    decisions.put(key, refined);
                    BotPathMemory.rememberMapAssessment(map.getId(), refined);
                    logger.debug("Bot {} combat advice for group {}: {} ({})",
                        new Object[] { bot.getName(), best.getId(), refined.isFavorable(), refined.getReason() });
                });
        }

        return local;
    }

    public static MonsterGroup findBestFavorableGroup(Characters bot, BotPersonality personality) {
        if(bot == null || bot.getCurrentMap() == null) return null;
        Decision decision = inspectMap(bot, personality);
        if(decision == null || !decision.isFavorable()) return null;
        return bot.getCurrentMap().getMonsterGroups().get(decision.getGroupId());
    }

    private static Decision localDecision(Characters bot, BotPersonality personality, MonsterGroup group) {
        int botLevel = bot.getExperience() != null ? bot.getExperience().getLevel() : 1;
        int botLife = Math.max(1, bot.getLife());
        int botPower = botLevel * 10 + botLife
            + bot.getStats().getEffect(EConstants.ADD_STRENGTH.getInt())
            + bot.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt())
            + bot.getStats().getEffect(EConstants.ADD_CHANCE.getInt())
            + bot.getStats().getEffect(EConstants.ADD_AGILITY.getInt());

        int monsterLevel = 0;
        int monsterLife = 0;
        int monsterDamage = 0;
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if(grade == null) continue;
            monsterLevel += grade.getLevel();
            monsterLife += grade.getLife();
            monsterDamage += Math.max(1, grade.getStrength() / 10 + grade.getIntel() / 14 + grade.getChance() / 14 + grade.getAgility() / 14);
        }

        double personalityRisk = 1.0;
        if(personality == BotPersonality.WARRIOR) personalityRisk = 1.25;
        else if(personality == BotPersonality.EXPLORER) personalityRisk = 1.05;
        else if(personality == BotPersonality.MERCHANT) personalityRisk = 0.75;

        double monsterThreat = monsterLevel * 8.0 + monsterLife * 0.65 + monsterDamage * 5.0;
        double score = (botPower * personalityRisk) / Math.max(1.0, monsterThreat);
        boolean favorable = score >= 0.95 && botLevel + 8 >= monsterLevel;
        double confidence = Math.min(0.95, Math.abs(score - 0.95) + 0.45);
        String reason = "heuristic score=" + String.format("%.2f", score)
            + " botLevel=" + botLevel + " monsterLevel=" + monsterLevel
            + " groupSize=" + group.getMemberCount();
        return new Decision(group.getId(), favorable, confidence, reason);
    }

    private static double groupAttractiveness(Characters bot, BotPersonality personality, MonsterGroup group) {
        Decision decision = localDecision(bot, personality, group);
        double levelBonus = 0;
        if(bot.getExperience() != null) {
            int botLevel = bot.getExperience().getLevel();
            int groupLevel = groupLevel(group);
            levelBonus = -Math.abs(botLevel - groupLevel) * 0.05;
        }
        return (decision.isFavorable() ? 10 : -5) + decision.getConfidence() + levelBonus;
    }

    private static int groupLevel(MonsterGroup group) {
        int total = 0;
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if(grade != null) total += grade.getLevel();
        }
        return total;
    }

    private static String summarize(Characters bot, BotPersonality personality, MonsterGroup group, Decision local) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bot ").append(bot.getName())
          .append(" personality=").append(personality)
          .append(" level=").append(bot.getExperience() != null ? bot.getExperience().getLevel() : 1)
          .append(" life=").append(bot.getLife())
          .append(" heuristic=").append(local.getReason())
          .append(". Monster group: ");
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            sb.append(entry.getTemplate().getName())
              .append(" grade ").append(entry.getGrade());
            if(grade != null) {
                sb.append(" lvl ").append(grade.getLevel())
                  .append(" hp ").append(grade.getLife())
                  .append(" str ").append(grade.getStrength())
                  .append(" int ").append(grade.getIntel())
                  .append(" cha ").append(grade.getChance())
                  .append(" agi ").append(grade.getAgility());
            }
            sb.append("; ");
        }
        sb.append("Answer only FIGHT or AVOID followed by a short reason.");
        return sb.toString();
    }

    private static Decision parseAdvice(int groupId, Decision fallback, String advice) {
        if(advice == null || advice.trim().isEmpty()) return fallback;
        String normalized = advice.trim().toUpperCase();
        boolean favorable;
        if(normalized.startsWith("FIGHT")) favorable = true;
        else if(normalized.startsWith("AVOID")) favorable = false;
        else return fallback;
        return new Decision(groupId, favorable, 0.85, "chatgpt: " + advice.trim());
    }

    private static String key(Characters bot, BotPersonality personality, MonsterGroup group) {
        int level = bot.getExperience() != null ? bot.getExperience().getLevel() : 1;
        return personality + ":" + level + ":" + groupSignature(group);
    }

    private static String groupSignature(MonsterGroup group) {
        StringBuilder sb = new StringBuilder();
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            if(sb.length() > 0) sb.append(',');
            sb.append(entry.getTemplate().getId()).append('~').append(entry.getGrade());
        }
        return sb.toString();
    }
}
