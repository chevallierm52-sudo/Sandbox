package org.dofus.objects.craft;

import java.util.Collections;
import java.util.Map;

/**
 * Recette d'artisanat Dofus 1.29.
 *
 * Une recette associe :
 *   - Un métier ({@link JobType}) et un niveau minimum requis
 *   - Des ingrédients : templateId → quantité
 *   - Un résultat : templateId de l'item produit + quantité produite
 *
 * Les recettes sont chargées depuis {@code craft_recipes} (voir craft_system.sql).
 */
public class CraftRecipe {

    public enum JobType {
        ALCHEMIST    (1,  "Alchimiste"),
        FARMER       (2,  "Paysan"),
        LUMBERJACK   (3,  "Bûcheron"),
        MINER        (4,  "Mineur"),
        FISHERMAN    (5,  "Pêcheur"),
        HUNTER       (6,  "Chasseur"),
        BAKER        (7,  "Boulanger"),
        SHOEMAKER    (8,  "Cordonnier"),
        JEWELER      (9,  "Bijoutier"),
        CRAFTSMAN    (10, "Bricoleur"),
        CARVER       (11, "Sculpteur"),
        TAILOR       (12, "Tailleur"),
        SMITH        (13, "Forgeur"),
        SHIELD_SMITH (14, "Forgeur de boucliers"),
        STAFF_CARVER (15, "Sculpteur de bâtons");

        public final int    id;
        public final String name;

        JobType(int id, String name) { this.id = id; this.name = name; }

        public static JobType fromId(int id) {
            for(JobType j : values()) if(j.id == id) return j;
            return null;
        }
    }

    private final int              id;
    private final JobType          job;
    private final int              levelRequired;
    private final Map<Integer,Integer> ingredients; // templateId → qty
    private final int              resultTemplateId;
    private final int              resultQty;

    public CraftRecipe(int id, JobType job, int levelRequired,
                       Map<Integer,Integer> ingredients,
                       int resultTemplateId, int resultQty) {
        this.id               = id;
        this.job              = job;
        this.levelRequired    = levelRequired;
        this.ingredients      = Collections.unmodifiableMap(ingredients);
        this.resultTemplateId = resultTemplateId;
        this.resultQty        = resultQty;
    }

    public int                 getId()              { return id;               }
    public JobType             getJob()             { return job;              }
    public int                 getLevelRequired()   { return levelRequired;    }
    public Map<Integer,Integer> getIngredients()    { return ingredients;      }
    public int                 getResultTemplateId(){ return resultTemplateId; }
    public int                 getResultQty()       { return resultQty;        }

    /** Vérifie que tous les ingrédients sont présents (interface avec Inventory). */
    public boolean canCraft(Map<Integer, Integer> playerItems) {
        for(Map.Entry<Integer,Integer> req : ingredients.entrySet()) {
            int have = playerItems.getOrDefault(req.getKey(), 0);
            if(have < req.getValue()) return false;
        }
        return true;
    }
}
