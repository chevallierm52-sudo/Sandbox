package org.dofus.network.game.handlers.parsers;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.MapsData;
import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.actors.BotBehavior;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.utils.ServerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur des commandes administrateur.
 *
 * Préfixe : '.' (ex : .kick Torvan, .goto Alyndra, .announce Serveur relancé dans 5 min)
 *
 * Hiérarchie de droits :
 *   - canMoveAllDirections() = bit 8192 = accès commandes de base (GM)
 *   - TODO : droits granulaires via EPermission (MODERATOR, ADMIN, OWNER)
 *
 * Commandes disponibles :
 *   .help                       — liste les commandes
 *   .info                       — statistiques serveur
 *   .kick <nom>                 — déconnecte un joueur
 *   .ban <nom>                  — bannit un joueur (flag BDD)
 *   .unban <nom>                — débannit un joueur
 *   .mute <nom>                 — retire le droit de parler
 *   .unmute <nom>               — rend la parole
 *   .goto <nom>                 — téléporte le GM vers un joueur
 *   .bring <nom>                — téléporte un joueur vers le GM
 *   .tp <mapId> [cellId]        — téléporte le GM vers une map
 *   .kamas <nom> <montant>      — donne des kamas
 *   .level <nom> <niveau>       — fixe le niveau d'un personnage
 *   .god                        — bascule l'invulnérabilité du GM
 *   .invis                      — bascule l'invisibilité du GM
 *   .announce <message>         — diffuse un message système à tous
 *   .reload                     — recharge les données PNJ/cartes (TODO)
 *   .speed <multiplicateur>     — multiplie la vitesse des bots (TODO)
 */
public class AdminParser {

    private static final Logger logger = LoggerFactory.getLogger(AdminParser.class);

    /** Préfixe reconnu pour les commandes admin. */
    public static final char PREFIX = '.';

    // ── Commandes ─────────────────────────────────────────────────────────────

    /**
     * Point d'entrée principal.
     * Appelé par {@link BasicParser#channelsMessage} quand le message commence par '.'.
     *
     * @param actor   Le personnage qui a envoyé la commande
     * @param session Sa session
     * @param client  Son GameClient (pour téléport)
     * @param raw     Le message brut SANS le préfixe '.' (ex : "kick Torvan")
     */
    public static void parse(Characters actor, IoSession session, GameClient client, String raw) {
        if(!isAdmin(actor)) {
            send(session, "§ Droits insuffisants.");
            return;
        }

        // Nettoyage du message reçu depuis le paquet BM
        raw = raw.trim();

        // Si le message contient encore le séparateur final du paquet
        if (raw.endsWith("|")) {
            raw = raw.substring(0, raw.length() - 1).trim();
        }

        // Si BasicParser a envoyé la commande AVEC le préfixe "."
        if (raw.startsWith(String.valueOf(PREFIX))) {
            raw = raw.substring(1).trim();
        }

        if (raw.isEmpty()) {
            send(session, "§ Commande vide. Tapez .help");
            return;
        }
        
        String[] parts = raw.trim().split("\\s+", 3);
        String cmd  = parts[0].toLowerCase();
        String arg1 = parts.length > 1 ? parts[1] : "";
        String arg2 = parts.length > 2 ? parts[2] : "";

        try {
            switch(cmd) {
                case "help":     cmdHelp(session);                         break;
                case "info":     cmdInfo(session);                         break;
                case "kick":     cmdKick(actor, session, arg1);            break;
                case "ban":      cmdBan(actor, session, arg1);             break;
                case "unban":    cmdUnban(actor, session, arg1);           break;
                case "mute":     cmdMute(actor, session, arg1);            break;
                case "unmute":   cmdUnmute(actor, session, arg1);          break;
                case "goto":     cmdGoto(actor, session, client, arg1);    break;
                case "bring":    cmdBring(actor, session, client, arg1);   break;
                case "tp":       cmdTp(actor, session, client, arg1, arg2);break;
                case "kamas":    cmdKamas(actor, session, arg1, arg2);     break;
                case "level":    cmdLevel(actor, session, arg1, arg2);     break;
                case "god":      cmdGod(actor, session);                   break;
                case "invis":    cmdInvis(actor, session);                 break;
                case "announce": cmdAnnounce(actor, session, arg1 + (arg2.isEmpty() ? "" : " " + arg2)); break;
                case "reload":   cmdReload(actor, session);                break;
                case "speed":    cmdSpeed(actor, session, arg1);           break;
                default:
                    send(session, "§ Commande inconnue : ." + cmd + " — tapez .help");
            }
        } catch(Exception e) {
            logger.warn("AdminParser error on '{}' by {}: {}",new Object[] { raw, actor.getName(), e.getMessage()});
            send(session, "§ Erreur : " + e.getMessage());
        }
    }

    // ── Implémentations ───────────────────────────────────────────────────────

    private static void cmdHelp(IoSession session) {
        send(session, "§ ── Commandes GM ──────────────────────────────────");
        send(session, "§ .info .kick <n> .ban <n> .unban <n> .mute <n> .unmute <n>");
        send(session, "§ .goto <n> .bring <n> .tp <mapId> [cellId]");
        send(session, "§ .kamas <n> <montant> .level <n> <lvl>");
        send(session, "§ .god .invis .announce <msg> .reload .speed <mult>");
    }

    private static void cmdInfo(IoSession session) {
        int online  = WorldData.getCharacters().size();
        long uptime = ServerMetrics.getUptimeSeconds();
        long h = uptime / 3600, m = (uptime % 3600) / 60, s = uptime % 60;
        send(session, String.format("§ Joueurs : %d - Uptime : %dh%02dm%02ds - RAM : %d Mo",
            online, h, m, s,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)));
    }

    private static void cmdKick(Characters actor, IoSession session, String name) {
        Characters target = findOnline(session, name);
        if(target == null) return;
        if(target == actor) { send(session, "§ Tu ne peux pas te kick toi-même."); return; }

        IoSession ts = getSession(target);
        if(ts != null) ts.close();
        logger.info("GM {} kicked {}", actor.getName(), name);
        send(session, "§ " + name + " a été déconnecté.");
    }

    private static void cmdBan(Characters actor, IoSession session, String name) {
        // Cherche en ligne d'abord, puis en BDD
        Characters target = WorldData.getCharacterByName().get(name);
        if(target != null) {
            // Kick immédiat
            IoSession ts = getSession(target);
            if(ts != null) ts.close();
        }
        // TODO : CharactersData.ban(name) — marquer en BDD + blocage login
        logger.info("GM {} banned {}", actor.getName(), name);
        send(session, "§ " + name + " a été banni. (persistance BDD à implémenter)");
    }

    private static void cmdUnban(Characters actor, IoSession session, String name) {
        // TODO : CharactersData.unban(name)
        logger.info("GM {} unbanned {}", actor.getName(), name);
        send(session, "§ " + name + " a été débanni. (persistance BDD à implémenter)");
    }

    private static void cmdMute(Characters actor, IoSession session, String name) {
        Characters target = findOnline(session, name);
        if(target == null) return;
        target.getRight().setCanChatWithAll(false);
        send(getSession(target), "§ Vous avez été réduit au silence par un modérateur.");
        send(session, "§ " + name + " a été muté.");
    }

    private static void cmdUnmute(Characters actor, IoSession session, String name) {
        Characters target = findOnline(session, name);
        if(target == null) return;
        target.getRight().setCanChatWithAll(true);
        send(getSession(target), "§ Votre droit de parole a été restauré.");
        send(session, "§ " + name + " a été démuté.");
    }

    private static void cmdGoto(Characters actor, IoSession session, GameClient client, String name) {
        Characters target = findOnline(session, name);
        if(target == null) return;
        RolePlayMovement.teleport(client, target.getCurrentMap(), target.getCurrentCell());
        send(session, "§ Téléporté vers " + name + " (map " + target.getCurrentMap().getId() + ").");
    }

    private static void cmdBring(Characters actor, IoSession session, GameClient client, String name) {
        Characters target = findOnline(session, name);
        if(target == null) return;
        GameClient targetClient = (GameClient) WorldData.getController(target.getId());
        if(targetClient == null) { send(session, "§ Impossible de récupérer le client de " + name + "."); return; }
        RolePlayMovement.teleport(targetClient, actor.getCurrentMap(), actor.getCurrentCell());
        send(session, "§ " + name + " téléporté vers vous.");
    }

    private static void cmdTp(Characters actor, IoSession session, GameClient client, String arg1, String arg2) {
        if(arg1.isEmpty()) { send(session, "§ Usage : .tp <mapId> [cellId]"); return; }
        int mapId;
        try { mapId = Integer.parseInt(arg1); }
        catch(NumberFormatException e) { send(session, "§ mapId invalide."); return; }

        MapTemplate map = MapsData.findById(mapId);
        if(map == null) { send(session, "§ Map " + mapId + " introuvable."); return; }

        short cell = 200;
        if(!arg2.isEmpty()) {
            try { cell = Short.parseShort(arg2); } catch(NumberFormatException ignored) {}
        }
        RolePlayMovement.teleport(client, map, cell);
        send(session, "§ Téléporté → map " + mapId + " cellule " + cell + ".");
    }

    private static void cmdKamas(Characters actor, IoSession session, String name, String amountStr) {
        if(name.isEmpty() || amountStr.isEmpty()) { send(session, "§ Usage : .kamas <nom> <montant>"); return; }
        Characters target = findOnline(session, name);
        if(target == null) return;
        long amount;
        try { amount = Long.parseLong(amountStr); }
        catch(NumberFormatException e) { send(session, "§ Montant invalide."); return; }

        target.setKamas(target.getKamas() + amount);
        CharactersData.update(target);

        IoSession ts = getSession(target);
        if(ts != null) ts.write("GA;5;" + target.getId() + ";" + target.getKamas());
        send(session, "§ " + amount + " kamas donnés à " + name + ".");
    }

    private static void cmdLevel(Characters actor, IoSession session, String name, String levelStr) {
        if(name.isEmpty() || levelStr.isEmpty()) { send(session, "§ Usage : .level <nom> <niveau>"); return; }
        Characters target = findOnline(session, name);
        if(target == null) return;

        short lvl;
        try {
            lvl = Short.parseShort(levelStr);
            if(lvl < 1 || lvl > 200) throw new NumberFormatException();
        } catch(NumberFormatException e) { send(session, "§ Niveau invalide (1-200)."); return; }

        // TODO : CharacterExperience.setLevel(lvl) + notif client
        send(session, "§ Niveau de " + name + " fixé à " + lvl + ". (recalcul stats à implémenter)");
    }

    private static void cmdGod(Characters actor, IoSession session) {
        // TODO : stocker un flag god sur Characters, annuler les dégâts en combat
        send(session, "§ Mode Dieu : non encore implémenté en combat (TODO).");
    }

    private static void cmdInvis(Characters actor, IoSession session) {
        // TODO : masquer du paquet GM, flag invisible sur Characters
        send(session, "§ Invisibilité : non encore implémentée (TODO).");
    }

    private static void cmdAnnounce(Characters actor, IoSession session, String message) {
        if(message.isEmpty()) { send(session, "§ Usage : .announce <message>"); return; }
        String packet = "Im036;" + message; // Im036 = message système jaune en jeu
        broadcast(packet);
        logger.info("ANNOUNCE by {}: {}", actor.getName(), message);
        send(session, "§ Message diffusé à " + WorldData.getCharacters().size() + " joueur(s).");
    }

    private static void cmdReload(Characters actor, IoSession session) {
        // TODO : Initialisation.reload() — rechargement à chaud des PNJ et cartes
        send(session, "§ Reload : non encore implémenté (TODO).");
    }

    private static void cmdSpeed(Characters actor, IoSession session, String multStr) {
        // TODO : exposer un facteur dans BotBehavior et l'ajuster ici
        send(session, "§ Speed bots : non encore implémenté (TODO).");
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Vérifie que le personnage a les droits GM (bit 8192). */
    public static boolean isAdmin(Characters actor) {
        return actor != null && actor.getRight().canMoveAllDirections();
    }

    private static Characters findOnline(IoSession session, String name) {
        if(name == null || name.isEmpty()) {
            send(session, "§ Nom manquant."); return null;
        }
        Characters target = WorldData.getCharacterByName().get(name);
        if(target == null || !target.isConnected()) {
            send(session, "§ Joueur '" + name + "' introuvable ou hors ligne."); return null;
        }
        return target;
    }

    private static IoSession getSession(Characters actor) {
        if(actor == null) return null;
        return WorldData.getSessionByAccount().get(actor.getOwner());
    }

    private static void send(IoSession session, String msg) {
        if(session != null && session.isConnected())
            session.write("cMKB|0||" + msg);
    }

    private static void broadcast(String packet) {
        for(Characters actor : new ArrayList<>(WorldData.getCharacters().values())) {
            IoSession s = WorldData.getSessionByAccount().get(actor.getOwner());
            if(s != null && s.isConnected()) s.write(packet);
        }
    }
}
