package org.dofus.objects.exchange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Échange joueur-joueur Dofus 1.29.
 *
 * Protocole :
 *   Initiateur envoie EX{targetId} → serveur envoie EX{initiatorId} à la cible.
 *   La cible accepte EX{initiatorId} → fenêtre d'échange ouverte des deux côtés (EW).
 *   Chaque joueur ajoute/retire des objets (EA/ER) et/ou des kamas (Em).
 *   Chaque joueur valide (EK). Quand les deux valident → échange exécuté.
 *   Annulation : EV de l'un des deux → annulation des deux côtés.
 *
 * Registre statique : characterId → Trade (pour retrouver la session d'échange active).
 */
public class Trade {

    private static final Logger logger = LoggerFactory.getLogger(Trade.class);

    /** Kamas max échangeables en une transaction (limite anti-triche). */
    private static final int MAX_KAMAS = 1_000_000_000;

    // ── Registre global ───────────────────────────────────────────────────────

    private static final Map<Integer, Trade> activeTrades = new ConcurrentHashMap<>();

    public static Trade getTradeFor(int characterId) { return activeTrades.get(characterId); }

    public static void removeTrade(int charId1, int charId2) {
        activeTrades.remove(charId1);
        activeTrades.remove(charId2);
    }

    // ── Modèle ────────────────────────────────────────────────────────────────

    public static class TradeSlot {
        public final int uid;        // UID de l'item
        public final int templateId;
        public final int quantity;

        public TradeSlot(int uid, int templateId, int quantity) {
            this.uid        = uid;
            this.templateId = templateId;
            this.quantity   = quantity;
        }
    }

    private final Characters initiator;
    private final Characters target;

    private final List<TradeSlot> initItems   = new ArrayList<>();
    private final List<TradeSlot> targetItems = new ArrayList<>();
    private       int             initKamas   = 0;
    private       int             targetKamas = 0;
    private       boolean         initOk      = false;
    private       boolean         targetOk    = false;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public Trade(Characters initiator, Characters target) {
        this.initiator = initiator;
        this.target    = target;
        activeTrades.put(initiator.getId(), this);
        activeTrades.put(target.getId(),    this);
        logger.debug("Trade ouvert entre {} et {}", initiator.getName(), target.getName());
    }

    // ── Gestion des slots ─────────────────────────────────────────────────────

    /**
     * Ajoute un item au côté de l'échangeur.
     * Si l'item existe déjà, met à jour la quantité.
     */
    public boolean addItem(Characters trader, int uid, int templateId, int quantity) {
        if(quantity <= 0) return false;
        resetValidation();
        List<TradeSlot> side = getSide(trader);
        // Mise à jour si déjà présent
        for(int i = 0; i < side.size(); i++) {
            if(side.get(i).uid == uid) {
                side.set(i, new TradeSlot(uid, templateId, quantity));
                return true;
            }
        }
        side.add(new TradeSlot(uid, templateId, quantity));
        return true;
    }

    /** Retire un item du côté de l'échangeur. */
    public boolean removeItem(Characters trader, int uid) {
        resetValidation();
        List<TradeSlot> side = getSide(trader);
        for(int i = 0; i < side.size(); i++) {
            if(side.get(i).uid == uid) { side.remove(i); return true; }
        }
        return false;
    }

    /** Définit les kamas mis sur la table par un joueur. */
    public boolean setKamas(Characters trader, int amount) {
        if(amount < 0 || amount > MAX_KAMAS) return false;
        if(amount > trader.getKamas()) return false;
        resetValidation();
        if(isInitiator(trader)) initKamas   = amount;
        else                    targetKamas = amount;
        return true;
    }

    /** Le joueur valide son côté de l'échange. Retourne true si les deux ont validé. */
    public boolean validate(Characters trader) {
        if(isInitiator(trader)) initOk   = true;
        else                    targetOk = true;
        return initOk && targetOk;
    }

    /**
     * Exécute l'échange physiquement.
     * ATTENTION : appel externe doit vérifier que {@link #validate} a retourné true.
     * Retourne false si une vérification de sécurité échoue.
     */
    public boolean execute() {
        // Vérification finale : kamas suffisants
        if(initiator.getKamas() < initKamas)  return false;
        if(target.getKamas()    < targetKamas) return false;

        // Transfert kamas
        initiator.setKamas(initiator.getKamas() - initKamas + targetKamas);
        target.setKamas(target.getKamas()       - targetKamas + initKamas);

        // Transfert des items initiateur → cible
        Inventory initInv   = initiator.getInventory();
        Inventory targetInv = target.getInventory();

        for(TradeSlot slot : initItems) {
            Item item = initInv.getByUid(slot.uid);
            if(item == null) continue;
            boolean ok = initInv.removeItem(slot.uid, slot.quantity);
            if(ok) {
                // Recrée l'item dans l'inventaire de la cible (même template, même quantité)
                targetInv.addItem(item.getTemplate(), slot.quantity);
            }
        }

        // Transfert des items cible → initiateur
        for(TradeSlot slot : targetItems) {
            Item item = targetInv.getByUid(slot.uid);
            if(item == null) continue;
            boolean ok = targetInv.removeItem(slot.uid, slot.quantity);
            if(ok) {
                initInv.addItem(item.getTemplate(), slot.quantity);
            }
        }

        logger.info("Trade exécuté : {} ↔ {} ({} kamas ↔ {} kamas, {} items ↔ {} items)",
        	new Object[] { initiator.getName(), target.getName(),
            initKamas, targetKamas, initItems.size(), targetItems.size()});

        return true;
    }

    // ── Sérialisation paquet ──────────────────────────────────────────────────

    /** Génère la liste d'items d'un côté pour le paquet EL. */
    public String buildItemList(Characters viewer) {
        StringBuilder sb = new StringBuilder();
        for(TradeSlot slot : getSide(isInitiator(viewer) ? initiator : target)) {
            if(sb.length() > 0) sb.append(';');
            sb.append(slot.uid).append('|').append(slot.templateId).append('|').append(slot.quantity);
        }
        return sb.toString();
    }

    public int getKamasFor(Characters trader) {
        return isInitiator(trader) ? initKamas : targetKamas;
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    public Characters getInitiator() { return initiator; }
    public Characters getTarget()    { return target;    }
    public Characters getOther(Characters me) {
        return isInitiator(me) ? target : initiator;
    }

    private boolean isInitiator(Characters c) {
        return c.getId() == initiator.getId();
    }

    private List<TradeSlot> getSide(Characters trader) {
        return isInitiator(trader) ? initItems : targetItems;
    }

    private void resetValidation() {
        initOk   = false;
        targetOk = false;
    }
}
