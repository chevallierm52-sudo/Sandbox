package org.dofus.objects.quests;

import java.util.List;

/**
 * Template de quête Dofus 1.29 — chargé depuis la table {@code quest_templates}.
 *
 * Structure d'une quête :
 *   - Plusieurs étapes ({@link QuestStep}) enchaînées
 *   - Chaque étape a des objectifs ({@link QuestObjective}) et des récompenses ({@link QuestReward})
 *   - Une étape est complétée quand tous ses objectifs sont satisfaits
 *   - La quête est complétée quand toutes ses étapes sont complétées
 *
 * Protocole (à compléter selon le client Dofus 1.29) :
 *   {@code QF{questId}}          — commencer une quête
 *   {@code Qf{questId}}          — abandonner une quête
 *   {@code Qr{questId}|{stepId}} — compléter une étape
 *   {@code QL}                   — liste des quêtes actives
 *
 * TODO : créer quest_system.sql + implémenter QuestParser.
 */
public class QuestTemplate {

    private final int         id;
    private final String      name;
    private final boolean     repeatable;
    private final List<QuestStep> steps;

    public QuestTemplate(int id, String name, boolean repeatable, List<QuestStep> steps) {
        this.id         = id;
        this.name       = name;
        this.repeatable = repeatable;
        this.steps      = steps;
    }

    public int             getId()         { return id;         }
    public String          getName()       { return name;       }
    public boolean         isRepeatable()  { return repeatable; }
    public List<QuestStep> getSteps()      { return steps;      }

    public QuestStep getStep(int index) {
        if(index < 0 || index >= steps.size()) return null;
        return steps.get(index);
    }

    // ── Étape de quête ────────────────────────────────────────────────────────

    public static class QuestStep {
        private final int                 id;
        private final String              description;
        private final List<QuestObjective> objectives;
        private final List<QuestReward>    rewards;

        public QuestStep(int id, String description,
                         List<QuestObjective> objectives, List<QuestReward> rewards) {
            this.id          = id;
            this.description = description;
            this.objectives  = objectives;
            this.rewards     = rewards;
        }

        public int    getId()          { return id;          }
        public String getDescription() { return description; }
        public List<QuestObjective> getObjectives() { return objectives; }
        public List<QuestReward>    getRewards()    { return rewards;    }
    }

    // ── Objectif de quête ─────────────────────────────────────────────────────

    public static class QuestObjective {
        public enum Type { TALK_NPC, KILL_MONSTER, COLLECT_ITEM, REACH_MAP }

        private final int    id;
        private final Type   type;
        private final int    targetId;  // npcId, monsterId, itemTemplateId, mapId
        private final int    quantity;  // pour KILL et COLLECT

        public QuestObjective(int id, Type type, int targetId, int quantity) {
            this.id       = id;
            this.type     = type;
            this.targetId = targetId;
            this.quantity = quantity;
        }

        public int  getId()       { return id;       }
        public Type getType()     { return type;     }
        public int  getTargetId() { return targetId; }
        public int  getQuantity() { return quantity; }
    }

    // ── Récompense de quête ───────────────────────────────────────────────────

    public static class QuestReward {
        private final long   kamas;
        private final long   experience;
        private final int    itemTemplateId; // 0 = pas d'objet
        private final int    itemQuantity;

        public QuestReward(long kamas, long experience, int itemTemplateId, int itemQuantity) {
            this.kamas          = kamas;
            this.experience     = experience;
            this.itemTemplateId = itemTemplateId;
            this.itemQuantity   = itemQuantity;
        }

        public long getKamas()           { return kamas;          }
        public long getExperience()      { return experience;     }
        public int  getItemTemplateId()  { return itemTemplateId; }
        public int  getItemQuantity()    { return itemQuantity;   }
    }
}
