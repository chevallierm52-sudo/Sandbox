package org.dofus.game.fight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.objects.monsters.MonsterTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestion des drops de monstres.
 *
 * Chaque monstre possède une liste de {@link DropEntry} :
 *   templateId d'item + probabilité sur 10 000 (ex : 5000 = 50%).
 *
 * Le calcul de drop tient compte du bonus de prospection des joueurs vainqueurs :
 *   Taux effectif = taux de base × (1 + prospection/100)
 *
 * Les drops sont chargés depuis {@code monster_drops} (table SQL optionnelle).
 * Sans données BDD, des drops par défaut sont appliqués (kamas seulement).
 *
 * Usage :
 *   {@code List<DropResult> drops = DropTable.roll(monsterTemplateId, prospection)}
 */
public class DropTable {

    private static final Logger logger = LoggerFactory.getLogger(DropTable.class);

    /** Probabilité max (= 100%). */
    public static final int MAX_RATE = 10_000;

    // ── Modèle ───────────────────────────────────────────────────────────────

    public static final class DropEntry {
        public final int templateId;
        public final int rate;       // sur 10 000
        public final int qtyMin;
        public final int qtyMax;

        public DropEntry(int templateId, int rate, int qtyMin, int qtyMax) {
            this.templateId = templateId;
            this.rate       = rate;
            this.qtyMin     = qtyMin;
            this.qtyMax     = qtyMax;
        }
    }

    public static final class DropResult {
        public final int templateId;
        public final int quantity;

        public DropResult(int templateId, int quantity) {
            this.templateId = templateId;
            this.quantity   = quantity;
        }
    }

    /** monsterTemplateId → liste de drops possibles */
    private static final Map<Integer, List<DropEntry>> table = new ConcurrentHashMap<>();

    // ── Enregistrement ────────────────────────────────────────────────────────

    /** Enregistre les drops d'un monstre (appelé par MonstersData.load). */
    public static void register(int monsterTemplateId, List<DropEntry> entries) {
        table.put(monsterTemplateId, new ArrayList<>(entries));
    }

    /** Enregistre un drop individuel. */
    public static void addDrop(int monsterTemplateId, int templateId, int rate, int qtyMin, int qtyMax) {
        table.computeIfAbsent(monsterTemplateId, k -> new ArrayList<>())
             .add(new DropEntry(templateId, rate, qtyMin, qtyMax));
    }

    // ── Calcul du loot ────────────────────────────────────────────────────────

    /**
     * Calcule le loot d'un monstre tué.
     *
     * @param monsterTemplateId ID du template monstre
     * @param prospection       Prospection totale des joueurs vainqueurs (somme)
     * @return Liste d'items droppés (peut être vide)
     */
    public static List<DropResult> roll(int monsterTemplateId, int prospection) {
        List<DropResult> results = new ArrayList<>();
        List<DropEntry>  entries = table.get(monsterTemplateId);
        if(entries == null || entries.isEmpty()) return results;

        // Bonus prospection : +1% par point de prospection au-delà de 100 (base 100%)
        double prosp = Math.max(100, prospection);
        double bonus = prosp / 100.0; // 100 prosp = ×1.0, 200 prosp = ×2.0

        for(DropEntry e : entries) {
            int effective = (int) Math.min(MAX_RATE, e.rate * bonus);
            int roll      = (int)(Math.random() * MAX_RATE);
            if(roll < effective) {
                int qty = e.qtyMin + (e.qtyMax > e.qtyMin
                    ? (int)(Math.random() * (e.qtyMax - e.qtyMin + 1))
                    : 0);
                results.add(new DropResult(e.templateId, qty));
                logger.debug("Drop : monsterId={} item={} qty={} (roll={}/{})",
                    new Object[] { monsterTemplateId, e.templateId, qty, roll, effective});
            }
        }
        return results;
    }

    /**
     * Calcule les kamas droppés par un monstre (grade).
     *
     * @param grade       Grade du monstre tué
     * @param prospection Prospection totale
     * @return Kamas gagnés
     */
    public static int rollKamas(MonsterTemplate.MonsterGrade grade, int prospection) {
        if(grade == null) return 0;
        int base = grade.getKamasMin() + (grade.getKamasMax() > grade.getKamasMin()
            ? (int)(Math.random() * (grade.getKamasMax() - grade.getKamasMin() + 1))
            : 0);
        double bonus = Math.max(1.0, prospection / 100.0);
        return (int)(base * bonus);
    }

    /** Charge des drops hardcodés par défaut si la table SQL est absente. */
    public static void loadDefaults() {
        // Tofu (id=1) → oreille de tofu (id=7) 50%
        addDrop(1, 7, 5000, 1, 2);
        // Larve Blanche (id=2) → oreille de larve fictive (id=6) 30%
        addDrop(2, 6, 3000, 1, 1);
        // Bouftou (id=5) → bois de frêne (id=6) 20%
        addDrop(5, 6, 2000, 1, 3);
        logger.info("DropTable : drops par défaut chargés pour 3 monstres");
    }

    public static int size() { return table.size(); }
}
