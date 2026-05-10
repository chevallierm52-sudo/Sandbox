package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.exchange.Trade;
import org.dofus.objects.items.Item;
import org.dofus.utils.PacketValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur des packets d'échange Dofus 1.29 (préfixe 'E').
 *
 * Échange joueur-joueur :
 *   EX{targetId}         — proposer un échange
 *   EA{uid}|{qty}        — ajouter un item
 *   ER{uid}              — retirer un item
 *   Em{amount}           — poser des kamas
 *   EK                   — valider son côté
 *   EV                   — annuler l'échange
 *
 * Boutique PNJ (TODO priorité 2) :
 *   EW{actorId}          — ouvrir boutique PNJ
 *   EB{templateId}       — acheter
 *   ES{uid}|{qty}|{prix} — vendre
 */
public class ExchangeParser {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeParser.class);

    // ── Entrée principale ─────────────────────────────────────────────────────

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'X': requestTrade(character, session, packet.substring(2)); break;
            case 'A': addToTrade(character, session, packet.substring(2));   break;
            case 'R': removeFromTrade(character, session, packet.substring(2)); break;
            case 'm': setKamas(character, session, packet.substring(2));     break;
            case 'K': validateTrade(character, session);                     break;
            case 'V': cancelTrade(character, session);                       break;
            case 'W': openShop(character, session, packet.substring(2));     break;
            case 'B': buyItem(character, session, packet.substring(2));      break;
            case 'S': sellItem(character, session, packet.substring(2));     break;
            default:  logger.debug("ExchangeParser : packet inconnu : {}", packet);
        }
    }

    // ── Échange joueur-joueur ─────────────────────────────────────────────────

    private static void requestTrade(Characters character, IoSession session, String args) {
        // Refuse si déjà en échange
        if(Trade.getTradeFor(character.getId()) != null) {
            session.write("BN");
            return;
        }

        int targetId;
        try { targetId = Integer.parseInt(args.trim()); }
        catch(NumberFormatException e) { return; }

        Characters target = WorldData.getCharacterById(targetId);
        if(target == null || !target.isConnected()) {
            session.write("BN");
            return;
        }
        // Cible doit être sur la même map
        if(character.getCurrentMap() == null || target.getCurrentMap() == null ||
           character.getCurrentMap().getId() != target.getCurrentMap().getId()) {
            session.write("BN");
            return;
        }

        // Envoi de la demande à la cible
        IoSession targetSession = WorldData.getSessionByAccount().get(target.getOwner());
        if(targetSession == null || !targetSession.isConnected()) {
            session.write("BN");
            return;
        }

        targetSession.write("EX" + character.getId());
        session.write("EX" + targetId); // confirmation à l'initiateur

        logger.debug("{} propose un échange à {}", character.getName(), target.getName());
    }

    private static void addToTrade(Characters character, IoSession session, String args) {
        Trade trade = Trade.getTradeFor(character.getId());
        if(trade == null) { session.write("BN"); return; }

        // Format : uid|qty
        String[] parts = args.split("\\|");
        if(parts.length < 2) { session.write("BN"); return; }

        long uid;
        int  qty;
        try {
            uid = Long.parseLong(parts[0]);
            qty = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid) || qty <= 0) {
            session.write("BN");
            return;
        }

        // Vérifie que l'item appartient au joueur et récupère le templateId
        Item item = character.getInventory().getByUid(uid);
        if(item == null || item.isEquipped() || item.getQuantity() < qty) {
            session.write("BN");
            return;
        }
        int templateId = item.getTemplate().getId();
        trade.addItem(character, (int) uid, templateId, qty);

        // Notifie les deux côtés
        sendToBoth(trade, "EA+" + uid + "|" + qty + "|" + character.getId());
    }

    private static void removeFromTrade(Characters character, IoSession session, String args) {
        Trade trade = Trade.getTradeFor(character.getId());
        if(trade == null) return;

        long uid;
        try { uid = Long.parseLong(args.trim()); }
        catch(NumberFormatException e) { return; }

        trade.removeItem(character, (int) uid);
        sendToBoth(trade, "ER+" + uid + "|" + character.getId());
    }

    private static void setKamas(Characters character, IoSession session, String args) {
        Trade trade = Trade.getTradeFor(character.getId());
        if(trade == null) return;

        int amount;
        try { amount = Integer.parseInt(args.trim()); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateKamas(session.getId(), amount)) { session.write("BN"); return; }

        if(trade.setKamas(character, amount)) {
            sendToBoth(trade, "Em" + amount + "|" + character.getId());
        } else {
            session.write("BN"); // kamas insuffisants
        }
    }

    private static void validateTrade(Characters character, IoSession session) {
        Trade trade = Trade.getTradeFor(character.getId());
        if(trade == null) return;

        boolean bothReady = trade.validate(character);
        sendToBoth(trade, "EK+" + character.getId());

        if(bothReady) {
            // Les deux ont validé — exécution
            if(trade.execute()) {
                sendToBoth(trade, "EV"); // fermeture
                // Mise à jour kamas côté client
                Characters init   = trade.getInitiator();
                Characters target = trade.getTarget();

                IoSession initSess   = WorldData.getSessionByAccount().get(init.getOwner());
                IoSession targetSess = WorldData.getSessionByAccount().get(target.getOwner());

                if(initSess   != null) initSess.write("Of=" + init.getKamas());
                if(targetSess != null) targetSess.write("Of=" + target.getKamas());

                logger.info("Trade {} ↔ {} exécuté avec succès",
                    init.getName(), target.getName());
            } else {
                sendToBoth(trade, "BN");
            }
            Trade.removeTrade(trade.getInitiator().getId(), trade.getTarget().getId());
        }
    }

    private static void cancelTrade(Characters character, IoSession session) {
        Trade trade = Trade.getTradeFor(character.getId());
        if(trade == null) { session.write("EV"); return; }

        sendToBoth(trade, "EV");
        Trade.removeTrade(trade.getInitiator().getId(), trade.getTarget().getId());
        logger.debug("{} a annulé l'échange", character.getName());
    }

    // ── Boutique PNJ (stub) ───────────────────────────────────────────────────

    private static void openShop(Characters character, IoSession session, String actorIdStr) {
        // TODO : impl boutique PNJ (EW + EL items)
        session.write("BN");
    }

    private static void buyItem(Characters character, IoSession session, String args) {
        // TODO : vérif kamas, débit, ajout item inventaire
        session.write("EB-1");
    }

    private static void sellItem(Characters character, IoSession session, String args) {
        // TODO : vérif item, retrait inventaire, crédit kamas
        session.write("ES-1");
    }

    // ── Utilitaire ────────────────────────────────────────────────────────────

    private static void sendToBoth(Trade trade, String packet) {
        sendTo(trade.getInitiator(), packet);
        sendTo(trade.getTarget(),    packet);
    }

    private static void sendTo(Characters chr, String packet) {
        if(chr == null) return;
        IoSession sess = WorldData.getSessionByAccount().get(chr.getOwner());
        if(sess != null && sess.isConnected()) sess.write(packet);
    }
}
