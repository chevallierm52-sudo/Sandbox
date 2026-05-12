package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Donnees officielles/importees des familiers.
 * Table attendue : pets(TemplateID, Type, Gap, StatsUp, Max, Gain, DeadTemplate).
 */
public final class PetsData {

    private static final Logger logger = LoggerFactory.getLogger(PetsData.class);
    private static final ConcurrentMap<Integer, PetTemplate> pets = new ConcurrentHashMap<Integer, PetTemplate>();

    private PetsData() {}

    public static void load() {
        pets.clear();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT TemplateID, Type, Gap, StatsUp, Max, Gain, DeadTemplate FROM pets");
                ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    PetTemplate template = new PetTemplate(
                            rs.getInt("TemplateID"),
                            rs.getInt("Type"),
                            rs.getString("Gap"),
                            parseStatsUp(rs.getString("StatsUp")),
                            rs.getInt("Max"),
                            rs.getInt("Gain"),
                            rs.getInt("DeadTemplate"));
                    pets.put(template.getTemplateId(), template);
                }
            }
            logger.info("PetsData : {} familier(s) charge(s)", pets.size());
        } catch(Exception e) {
            logger.warn("PetsData : table absente ou erreur, familiers limites aux templates : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static PetTemplate get(int templateId) {
        return pets.get(templateId);
    }

    public static boolean isKnownPet(int templateId) {
        return pets.containsKey(templateId);
    }

    private static Map<Integer, Set<Integer>> parseStatsUp(String raw) {
        if(raw == null || raw.trim().isEmpty()) return Collections.emptyMap();

        Map<Integer, Set<Integer>> result = new HashMap<Integer, Set<Integer>>();
        for(String statPart : raw.split(";")) {
            String clean = statPart.trim();
            if(clean.isEmpty()) continue;

            String[] split = clean.split("\\|", 2);
            if(split.length < 2) continue;

            int effectId;
            try {
                effectId = Integer.parseInt(split[0].trim(), 16);
            } catch(NumberFormatException e) {
                continue;
            }

            Set<Integer> foods = new HashSet<Integer>();
            for(String token : split[1].split("[#|]")) {
                String foodToken = token.trim();
                if(foodToken.isEmpty()) continue;
                int comma = foodToken.indexOf(',');
                if(comma >= 0) foodToken = foodToken.substring(0, comma);
                try {
                    foods.add(Integer.parseInt(foodToken));
                } catch(NumberFormatException ignored) {}
            }

            if(!foods.isEmpty()) result.put(effectId, foods);
        }
        return result;
    }

    public static final class PetTemplate {
        private final int templateId;
        private final int type;
        private final String gap;
        private final Map<Integer, Set<Integer>> foodsByEffect;
        private final int max;
        private final int gain;
        private final int deadTemplateId;

        private PetTemplate(int templateId, int type, String gap, Map<Integer, Set<Integer>> foodsByEffect,
                int max, int gain, int deadTemplateId) {
            this.templateId = templateId;
            this.type = type;
            this.gap = gap != null ? gap : "";
            this.foodsByEffect = foodsByEffect;
            this.max = max;
            this.gain = gain;
            this.deadTemplateId = deadTemplateId;
        }

        public int getTemplateId() { return templateId; }
        public int getType() { return type; }
        public String getGap() { return gap; }
        public Map<Integer, Set<Integer>> getFoodsByEffect() { return foodsByEffect; }
        public int getMax() { return max; }
        public int getGain() { return gain; }
        public int getDeadTemplateId() { return deadTemplateId; }

        public int effectForFood(int foodTemplateId, int foodTypeId) {
            int searchedId = type == 3 ? foodTypeId : foodTemplateId;
            for(Map.Entry<Integer, Set<Integer>> entry : foodsByEffect.entrySet()) {
                if(entry.getValue().contains(searchedId)) return entry.getKey();
            }
            return 0;
        }

        /** Ancienne signature gardee pour compatibilite interne. */
        public int effectForFood(int foodTemplateId) {
            return effectForFood(foodTemplateId, foodTemplateId);
        }
    }
}
