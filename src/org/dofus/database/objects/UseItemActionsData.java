package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.items.UseItemAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Charge la table use_item_actions sans modifier la base existante.
 * Un template peut avoir plusieurs actions : soin + bonus stat + consommation, etc.
 */
public class UseItemActionsData {

    private static final Logger logger = LoggerFactory.getLogger(UseItemActionsData.class);
    private static final ConcurrentMap<Integer, List<UseItemAction>> actionsByTemplate = new ConcurrentHashMap<Integer, List<UseItemAction>>();

    public static void load() {
        actionsByTemplate.clear();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT template, type, args FROM use_item_actions ORDER BY template, type")) {
                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        int template = rs.getInt("template");
                        UseItemAction action = new UseItemAction(template, rs.getInt("type"), rs.getString("args"));
                        List<UseItemAction> list = actionsByTemplate.get(template);
                        if(list == null) {
                            list = Collections.synchronizedList(new ArrayList<UseItemAction>());
                            actionsByTemplate.put(template, list);
                        }
                        list.add(action);
                    }
                }
            }
            registerFallbackActions();
            logger.info("UseItemActionsData : {} template(s), {} action(s) charges", actionsByTemplate.size(), countActions());
        } catch(Exception e) {
            logger.warn("UseItemActionsData : table absente ou erreur, fallback use-item seulement : {}", e.getMessage());
            registerFallbackActions();
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static List<UseItemAction> getActions(int templateId) {
        List<UseItemAction> actions = actionsByTemplate.get(templateId);
        if(actions == null) return Collections.emptyList();
        return new ArrayList<UseItemAction>(actions);
    }

    public static boolean hasActions(int templateId) {
        List<UseItemAction> actions = actionsByTemplate.get(templateId);
        return actions != null && !actions.isEmpty();
    }

    private static void registerFallbackActions() {
        // Fallbacks observés dans les tests GM / logs. Ils ne remplacent jamais
        // les données SQL existantes : ils complètent uniquement les templates
        // absents pour éviter les OU -> BN sur une base partielle.
        fallback(806,  8, "125,1");   // Petit Parchemin de Vitalité
        fallback(806,  5, "");
        fallback(809,  8, "123,1");   // Petit Parchemin de Chance
        fallback(809,  5, "");
        fallback(1183, 10, "29,33");  // Potion de Mini Soin Supérieure
        fallback(1183, 5, "");
        fallback(6965, 0, "9648,30"); // Potion de cité : destination constatée dans les logs
        fallback(6965, 5, "");
        fallback(7423, 0, "6159,211"); // Oeuf de Larve Dorée : arène / scène associée constatée
        fallback(7423, 5, "");
    }

    private static void fallback(int template, int type, String args) {
        List<UseItemAction> list = actionsByTemplate.get(template);
        if(list == null) {
            list = Collections.synchronizedList(new ArrayList<UseItemAction>());
            actionsByTemplate.put(template, list);
        }
        for(UseItemAction existing : list) {
            if(existing.getType() == type) return;
        }
        list.add(new UseItemAction(template, type, args));
    }

    private static int countActions() {
        int total = 0;
        for(List<UseItemAction> list : actionsByTemplate.values()) total += list.size();
        return total;
    }
}
