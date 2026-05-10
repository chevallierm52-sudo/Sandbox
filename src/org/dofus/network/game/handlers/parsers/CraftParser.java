package org.dofus.network.game.handlers.parsers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.Connector;
import org.dofus.database.objects.ItemsData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.craft.CraftRecipe;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur d'artisanat Dofus 1.29 (préfixe 'M' = métier).
 *
 * Protocole :
 *   Client → Serveur :
 *     ML{jobId}              — Lister les recettes du métier
 *     MC{recipeId}|{qty}     — Lancer un craft
 *     Mx{jobId}              — Infos sur un métier
 *
 *   Serveur → Client :
 *     ML{recipeId}|{result}|{ingredients};... — Liste recettes
 *     MC+{uid}|{templateId}  — Craft réussi
 *     MC-{code}              — Craft échoué
 *     MK{xp}                 — XP de métier gagnée
 *
 * Branchement : {@code RolePlayHandler case 'M' → CraftParser.parse()}.
 */
public class CraftParser {

    private static final Logger logger = LoggerFactory.getLogger(CraftParser.class);

    /** Cache des recettes chargées : recipeId → CraftRecipe */
    private static final Map<Integer, CraftRecipe> recipes = new ConcurrentHashMap<>();

    // ── Chargement ────────────────────────────────────────────────────────────

    /**
     * Charge toutes les recettes depuis la base.
     * À appeler dans {@code Initialisation.init()}.
     */
    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.id, r.job_id, r.level_required, r.result_template_id, r.result_qty, " +
                    "i.ingredient_template_id, i.quantity " +
                    "FROM craft_recipes r " +
                    "JOIN craft_ingredients i ON i.recipe_id = r.id " +
                    "ORDER BY r.id");
                ResultSet rs = ps.executeQuery()) {

                Map<Integer, CraftRecipe.JobType>    jobMap  = new HashMap<>();
                Map<Integer, Integer>                lvlMap  = new HashMap<>();
                Map<Integer, Integer>                resMap  = new HashMap<>();
                Map<Integer, Integer>                qtyMap  = new HashMap<>();
                Map<Integer, Map<Integer,Integer>>   ingMap  = new HashMap<>();

                while(rs.next()) {
                    int recipeId = rs.getInt("id");
                    if(!ingMap.containsKey(recipeId)) {
                        jobMap.put(recipeId, CraftRecipe.JobType.fromId(rs.getInt("job_id")));
                        lvlMap.put(recipeId, rs.getInt("level_required"));
                        resMap.put(recipeId, rs.getInt("result_template_id"));
                        qtyMap.put(recipeId, rs.getInt("result_qty"));
                        ingMap.put(recipeId, new HashMap<>());
                    }
                    ingMap.get(recipeId).put(
                        rs.getInt("ingredient_template_id"),
                        rs.getInt("quantity")
                    );
                }

                for(int rid : ingMap.keySet()) {
                    CraftRecipe.JobType job = jobMap.get(rid);
                    if(job == null) continue;
                    recipes.put(rid, new CraftRecipe(
                        rid, job, lvlMap.get(rid),
                        ingMap.get(rid),
                        resMap.get(rid), qtyMap.get(rid)
                    ));
                }
            }
            logger.info("CraftParser : {} recettes chargées", recipes.size());
        } catch(Exception e) {
            logger.warn("CraftParser.load() : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    // ── Entrée principale ─────────────────────────────────────────────────────

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'L': listRecipes(character, session, packet.substring(2)); break;
            case 'C': craft(character, session, packet.substring(2));       break;
            case 'x': jobInfo(character, session, packet.substring(2));     break;
            default:  logger.debug("CraftParser : packet inconnu : {}", packet);
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static void listRecipes(Characters character, IoSession session, String jobIdStr) {
        int jobId;
        try { jobId = Integer.parseInt(jobIdStr.trim()); }
        catch(NumberFormatException e) { return; }

        CraftRecipe.JobType job = CraftRecipe.JobType.fromId(jobId);
        if(job == null) return;

        StringBuilder sb = new StringBuilder("ML");
        for(CraftRecipe recipe : recipes.values()) {
            if(recipe.getJob() != job) continue;
            // Format : recipeId|resultId|resultQty|ingId,ingQty;ingId,ingQty
            sb.append(recipe.getId()).append('|')
              .append(recipe.getResultTemplateId()).append('|')
              .append(recipe.getResultQty()).append('|');
            boolean first = true;
            for(Map.Entry<Integer,Integer> ing : recipe.getIngredients().entrySet()) {
                if(!first) sb.append(';');
                sb.append(ing.getKey()).append(',').append(ing.getValue());
                first = false;
            }
            sb.append('~');
        }
        session.write(sb.toString());
    }

    private static void craft(Characters character, IoSession session, String args) {
        // Format : recipeId|qty
        String[] parts = args.split("\\|");
        if(parts.length < 2) { session.write("MC-1"); return; }

        int recipeId, qty;
        try {
            recipeId = Integer.parseInt(parts[0]);
            qty      = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { session.write("MC-1"); return; }

        if(qty <= 0 || qty > 100) { session.write("MC-2"); return; } // quantité invalide

        CraftRecipe recipe = recipes.get(recipeId);
        if(recipe == null) { session.write("MC-1"); return; }

        // Construire la carte templateId → quantité disponible dans l'inventaire
        Inventory inv = character.getInventory();
        Map<Integer, Integer> available = buildAvailableMap(inv);

        // Vérifier que chaque ingrédient est présent en quantité suffisante × qty
        Map<Integer, Integer> needed = scaleIngredients(recipe.getIngredients(), qty);
        if(!hasEnough(available, needed)) {
            session.write("MC-3"); // ingrédients insuffisants
            return;
        }

        // Consommer les ingrédients
        for(Map.Entry<Integer, Integer> req : needed.entrySet()) {
            consumeIngredient(inv, session, req.getKey(), req.getValue());
        }

        // Donner le résultat
        int resultTplId = recipe.getResultTemplateId();
        int resultQty   = recipe.getResultQty() * qty;
        ItemTemplate resultTpl = ItemsData.getTemplate(resultTplId);
        if(resultTpl != null) {
            Item newItem = inv.addItem(resultTpl, resultQty);
            session.write(Inventory.buildOAPacket(newItem));
        }
        session.write("MC+" + resultTplId + "|" + resultQty);
        session.write("MK10"); // XP métier (TODO : calcul réel selon niveau recette)

        // Pods mis à jour
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());

        logger.debug("{} craft recette {} ×{} → item {}", new Object[] { character.getName(),
            recipeId, qty, resultTplId});
    }

    private static void jobInfo(Characters character, IoSession session, String jobIdStr) {
        // TODO : renvoyer niveau de métier + XP du personnage dans ce métier
        session.write("Mx0|1|0|0"); // jobId|level|xp|xpNextLevel placeholder
    }

    public static CraftRecipe getRecipe(int id) { return recipes.get(id); }
    public static int size()                     { return recipes.size(); }

    // ── Utilitaires craft ─────────────────────────────────────────────────────

    /** Construit templateId → quantité totale dans l'inventaire (sac uniquement). */
    private static Map<Integer, Integer> buildAvailableMap(Inventory inv) {
        Map<Integer, Integer> map = new HashMap<>();
        for(Item item : inv.getBag()) {
            int tplId = item.getTemplate().getId();
            map.put(tplId, map.getOrDefault(tplId, 0) + item.getQuantity());
        }
        return map;
    }

    /** Multiplie les quantités requises par qty. */
    private static Map<Integer, Integer> scaleIngredients(Map<Integer,Integer> base, int qty) {
        Map<Integer, Integer> scaled = new HashMap<>();
        for(Map.Entry<Integer,Integer> e : base.entrySet()) {
            scaled.put(e.getKey(), e.getValue() * qty);
        }
        return scaled;
    }

    /** Vérifie que available contient chaque templateId de needed en quantité suffisante. */
    private static boolean hasEnough(Map<Integer,Integer> available, Map<Integer,Integer> needed) {
        for(Map.Entry<Integer,Integer> e : needed.entrySet()) {
            if(available.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        return true;
    }

    /** Consomme qty unités du templateId depuis le sac. Envoie OR/OM au client. */
    private static void consumeIngredient(Inventory inv, IoSession session,
                                          int templateId, int qty) {
        int remaining = qty;
        for(Item item : new ArrayList<>(inv.getBag())) {
            if(item.getTemplate().getId() != templateId) continue;
            int take = Math.min(remaining, item.getQuantity());
            inv.removeItem(item.getUid(), take);
            if(inv.getByUid(item.getUid()) == null) {
                session.write(Inventory.buildORPacket(item.getUid()));
                ItemsData.delete(item.getUid());
            } else {
                session.write(Inventory.buildOMPacket(item));
                ItemsData.update(item);
            }
            remaining -= take;
            if(remaining <= 0) break;
        }
    }
}
