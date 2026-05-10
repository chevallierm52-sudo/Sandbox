package org.dofus.network.game.handlers.parsers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.Connector;
import org.dofus.database.objects.ItemsData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemTemplate;
import org.dofus.utils.PacketValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur banque Dofus 1.29 (coffre du compte).
 *
 * Protocole (packets 'B') :
 *   Client → Serveur :
 *     Bd           — Ouvrir la banque (depuis un PNJ banquier)
 *     Bk{amount}   — Déposer des kamas
 *     Bq{amount}   — Retirer des kamas
 *     Bi{uid}|{qty} — Déposer un item
 *     Bo{uid}|{qty} — Retirer un item
 *
 *   Serveur → Client :
 *     Bd{kamasBanque}|{items...} — Ouverture avec solde et items
 *     Bk+{kamas}   — Dépôt kamas OK
 *     Bq+{kamas}   — Retrait kamas OK
 *     Bi+{uid}     — Dépôt item OK
 *     Bo+{uid}     — Retrait item OK
 *
 * Coût d'ouverture : 10 kamas (taxe Dofus 1.29 standard, configurable).
 *
 * Branchement : {@code RolePlayHandler case 'B' → BankParser.parse()}.
 */
public class BankParser {

    private static final Logger logger = LoggerFactory.getLogger(BankParser.class);

    /** Coût d'ouverture de la banque en kamas. */
    public static final int OPEN_COST = 10;

    /** Cache des kamas en banque par compte (accountId → kamas). */
    private static final Map<Integer, Long> kamasCache = new ConcurrentHashMap<>();

    // ── Entrée principale ─────────────────────────────────────────────────────

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'd': openBank(character, session);                            break;
            case 'k': depositKamas(character, session, packet.substring(2));  break;
            case 'q': withdrawKamas(character, session, packet.substring(2)); break;
            case 'i': depositItem(character, session, packet.substring(2));   break;
            case 'o': withdrawItem(character, session, packet.substring(2));  break;
            default:  logger.debug("BankParser : packet inconnu : {}", packet);
        }
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    private static void openBank(Characters character, IoSession session) {
        if(character.getKamas() < OPEN_COST) {
            session.write("Im18"); // message "pas assez de kamas"
            return;
        }
        character.setKamas(character.getKamas() - OPEN_COST);
        session.write("Of-" + OPEN_COST);

        long bankKamas = getBankKamas(character);

        // Paquet d'ouverture : Bd{kamasBanque}|{items...}
        // Chaque item : uid~templateId~qty~position~effects
        StringBuilder bankContent = new StringBuilder("Bd");
        bankContent.append(bankKamas).append('|');
        appendBankItems(character, bankContent);
        session.write(bankContent.toString());

        logger.debug("{} ouvre sa banque (solde={}k, banque={}k)",
        		new Object[] { character.getName(), character.getKamas(), bankKamas});
    }

    // ── Kamas ─────────────────────────────────────────────────────────────────

    private static void depositKamas(Characters character, IoSession session, String args) {
        long amount;
        try { amount = Long.parseLong(args.trim()); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateKamas(session.getId(), amount)) { session.write("BN"); return; }
        if(amount <= 0 || amount > character.getKamas()) { session.write("BN"); return; }

        character.setKamas(character.getKamas() - amount);
        addBankKamas(character, amount);

        session.write("Of-" + amount);
        session.write("Bk+" + amount);
        logger.debug("{} dépose {} kamas en banque", character.getName(), amount);
    }

    private static void withdrawKamas(Characters character, IoSession session, String args) {
        long amount;
        try { amount = Long.parseLong(args.trim()); }
        catch(NumberFormatException e) { return; }

        if(!PacketValidator.validateKamas(session.getId(), amount)) { session.write("BN"); return; }

        long bankKamas = getBankKamas(character);
        if(amount <= 0 || amount > bankKamas) { session.write("BN"); return; }

        character.setKamas(character.getKamas() + amount);
        addBankKamas(character, -amount);

        session.write("Of+" + amount);
        session.write("Bq+" + amount);
        logger.debug("{} retire {} kamas de la banque", character.getName(), amount);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private static void depositItem(Characters character, IoSession session, String args) {
        // Format : uid|qty
        String[] parts = args.split("\\|");
        if(parts.length < 2) { session.write("BN"); return; }
        long uid;
        int  qty;
        try { uid = Long.parseLong(parts[0]); qty = Integer.parseInt(parts[1]); }
        catch(NumberFormatException e) { session.write("BN"); return; }

        if(!PacketValidator.validateItemId(session.getId(), (int) uid) || qty <= 0) {
            session.write("BN");
            return;
        }

        Inventory inv  = character.getInventory();
        Item      item = inv.getByUid(uid);
        if(item == null || item.isEquipped()) { session.write("BN"); return; }
        if(item.getQuantity() < qty)          { session.write("BN"); return; }

        // Retire du sac
        inv.removeItem(uid, qty);
        session.write(Inventory.buildORPacket(uid));   // OR si supprimé
        if(item.getQuantity() > 0)
            session.write(Inventory.buildOMPacket(item));  // OM si réduit

        // Enregistre en banque (BDD) — on réutilise templateId comme proxy
        addBankKamas(character, 0); // force flush du cache (no-op kamas)
        saveBankItem(character, (int) item.getTemplate().getId(), qty);

        session.write("Bi+" + uid);
        logger.debug("{} dépose item {} (qty={}) en banque", new Object[] { character.getName(), uid, qty});
    }

    private static void withdrawItem(Characters character, IoSession session, String args) {
        String[] parts = args.split("\\|");
        if(parts.length < 2) { session.write("BN"); return; }
        long uid;
        int  qty;
        try { uid = Long.parseLong(parts[0]); qty = Integer.parseInt(parts[1]); }
        catch(NumberFormatException e) { session.write("BN"); return; }

        // Retrait BDD — lookup du templateId depuis bank_items
        int templateId = getBankItemTemplate(character, (int) uid);
        if(templateId <= 0) { session.write("BN"); return; }

        ItemTemplate tpl = ItemsData.getTemplate(templateId);
        if(tpl == null) { session.write("BN"); return; }

        // Recrée l'item dans l'inventaire du personnage
        Item newItem = character.getInventory().addItem(tpl, qty);
        removeBankItem(character, (int) uid);

        session.write(Inventory.buildOAPacket(newItem));
        session.write("Bo+" + uid);
        logger.debug("{} retire item {} (qty={}) de la banque", new Object[] { character.getName(), uid, qty});
    }

    // ── Persistance items banque ──────────────────────────────────────────────

    private static void saveBankItem(Characters character, int templateId, int qty) {
        int accountId = character.getOwner().getId();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            // Upsert simple : ajoute ou augmente la quantité
            try(PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bank_items (account_id, template_id, quantity) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE quantity = quantity + ?")) {
                ps.setInt(1, accountId);
                ps.setInt(2, templateId);
                ps.setInt(3, qty);
                ps.setInt(4, qty);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur sauvegarde item banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    private static int getBankItemTemplate(Characters character, int rowId) {
        int accountId = character.getOwner().getId();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT template_id FROM bank_items WHERE id=? AND account_id=?")) {
                ps.setInt(1, rowId);
                ps.setInt(2, accountId);
                try(ResultSet rs = ps.executeQuery()) {
                    if(rs.next()) return rs.getInt("template_id");
                }
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur lecture item banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
        return -1;
    }

    private static void removeBankItem(Characters character, int rowId) {
        int accountId = character.getOwner().getId();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM bank_items WHERE id=? AND account_id=?")) {
                ps.setInt(1, rowId);
                ps.setInt(2, accountId);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur suppression item banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    // ── Persistance kamas banque ──────────────────────────────────────────────

    private static long getBankKamas(Characters character) {
        int accountId = character.getOwner().getId();
        if(kamasCache.containsKey(accountId)) return kamasCache.get(accountId);

        long kamas = 0;
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT bank_kamas FROM accounts WHERE id = ?")) {
                ps.setInt(1, accountId);
                try(ResultSet rs = ps.executeQuery()) {
                    if(rs.next()) kamas = rs.getLong("bank_kamas");
                }
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur lecture banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
        kamasCache.put(accountId, kamas);
        return kamas;
    }

    private static void addBankKamas(Characters character, long delta) {
        int  accountId = character.getOwner().getId();
        long newTotal  = Math.max(0, getBankKamas(character) + delta);
        kamasCache.put(accountId, newTotal);

        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "UPDATE accounts SET bank_kamas = ? WHERE id = ?")) {
                ps.setLong(1, newTotal);
                ps.setInt(2, accountId);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur sauvegarde banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    /** Vide le cache d'un compte (déconnexion). */
    public static void evictCache(int accountId) {
        kamasCache.remove(accountId);
    }

    /** Construit la liste des items en banque dans le paquet Bd (format OL entry). */
    private static void appendBankItems(Characters character, StringBuilder sb) {
        int accountId = character.getOwner().getId();
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, template_id, quantity FROM bank_items WHERE account_id = ?")) {
                ps.setInt(1, accountId);
                try(ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while(rs.next()) {
                        int rowId      = rs.getInt("id");
                        int templateId = rs.getInt("template_id");
                        int qty        = rs.getInt("quantity");
                        // Format simplifié : id~templateId~qty~-1~  (aucun effet rollé)
                        if(!first) sb.append('|');
                        sb.append(rowId).append('~').append(templateId).append('~').append(qty).append("~-1~");
                        first = false;
                    }
                }
            }
        } catch(Exception e) {
            logger.warn("BankParser : erreur lecture items banque : {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }
}
