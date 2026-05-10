package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.ItemsData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.utils.PacketValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur d'inventaire Dofus 1.29 (préfixe 'O').
 *
 * Protocole :
 *   Client → Serveur :
 *     Oe{uid}|{position}  — Équiper un item dans un slot
 *     Ou{uid}             — Déséquiper un item (retour sac)
 *     Od{uid}|{qty}       — Supprimer un item
 *     Os{uid1}|{uid2}     — Échanger deux positions (sac ↔ équipé)
 *
 *   Serveur → Client :
 *     OA{entry}           — Ajout d'un item
 *     OM{uid}|{entry}     — Modification d'un item
 *     OR{uid}             — Suppression d'un item
 *     Ow{used}|{max}      — Mise à jour pods
 *
 * Branchement : {@code RolePlayHandler case 'O' → InventoryParser.parse()}.
 */
public class InventoryParser {

    private static final Logger logger = LoggerFactory.getLogger(InventoryParser.class);

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'e': equipItem(character, session, packet.substring(2));   break;
            case 'u': unequipItem(character, session, packet.substring(2)); break;
            case 'd': dropItem(character, session, packet.substring(2));    break;
            default:
                logger.debug("InventoryParser : paquet inconnu : {}", packet);
        }
    }

    // ── Équiper ───────────────────────────────────────────────────────────────

    private static void equipItem(Characters character, IoSession session, String args) {
        // Format : uid|position  (position ignorée — on utilise le slot par défaut)
        String[] parts = args.split("\\|");
        long uid;
        try { uid = Long.parseLong(parts[0]); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid)) return;

        Inventory inv  = character.getInventory();
        Item      item = inv.getByUid(uid);
        if(item == null || item.isEquipped()) return;

        Item displaced = inv.equip(uid);
        if(item.getPosition() < 0) return; // équipement impossible (non équipable)

        // Notifie le client : OM item équipé
        session.write(Inventory.buildOMPacket(item));

        // Si un item a été déplacé vers le sac → OM aussi
        if(displaced != null) {
            session.write(Inventory.buildOMPacket(displaced));
        }

        // Mise à jour pods
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());

        logger.debug("{} équipe item uid={} pos={}", new Object[] { character.getName(), uid, item.getPosition()});
    }

    // ── Déséquiper ────────────────────────────────────────────────────────────

    private static void unequipItem(Characters character, IoSession session, String args) {
        long uid;
        try { uid = Long.parseLong(args.trim()); }
        catch(NumberFormatException e) { return; }

        Inventory inv  = character.getInventory();
        Item      item = inv.getByUid(uid);
        if(item == null || !item.isEquipped()) return;

        inv.unequip(uid);
        session.write(Inventory.buildOMPacket(item));
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());

        logger.debug("{} déséquipe item uid={}", character.getName(), uid);
    }

    // ── Supprimer ─────────────────────────────────────────────────────────────

    private static void dropItem(Characters character, IoSession session, String args) {
        // Format : uid|qty
        String[] parts = args.split("\\|");
        if(parts.length < 2) return;
        long uid;
        int  qty;
        try {
            uid = Long.parseLong(parts[0]);
            qty = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid) || qty <= 0) return;

        Inventory inv  = character.getInventory();
        Item      item = inv.getByUid(uid);
        if(item == null || item.isEquipped()) return; // refus suppression si équipé

        boolean removed = inv.removeItem(uid, qty);
        if(!removed) return;

        if(inv.getByUid(uid) == null) {
            // Supprimé totalement
            session.write(Inventory.buildORPacket(uid));
            ItemsData.delete(uid);
        } else {
            // Quantité réduite
            session.write(Inventory.buildOMPacket(item));
            ItemsData.update(item);
        }

        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        logger.debug("{} supprime {} × item uid={}", new Object[] { character.getName(), qty, uid});
    }
}
