package org.dofus.objects.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inventaire d'un personnage.
 *
 * Gère :
 *  - Le sac (objets non équipés, position == -1)
 *  - Les slots d'équipement (position 1-10)
 *  - Les kamas (délégués à {@code Characters.kamas}, ici uniquement la logique pods)
 *
 * Protocole :
 *  - {@code OL} — liste complète au login
 *  - {@code OA} — ajout d'un objet
 *  - {@code OM} — modification (quantité, position)
 *  - {@code OR} — suppression
 *  - {@code Ow} — mise à jour pods utilisés
 *
 * Chaque modification notifie le client via un {@code ItemChangeListener} (ou session directe).
 */
public class Inventory {

    /** Générateur d'UID unique par instance de serveur (BDD devrait surpasser cela). */
    private static final AtomicLong UID_GEN = new AtomicLong(1_000_000L);

    /** uid → Item */
    private final ConcurrentMap<Long, Item> items = new ConcurrentHashMap<>();

    // ── Accès aux items ───────────────────────────────────────────────────────

    public Item getByUid(long uid) {
        return items.get(uid);
    }

    public Collection<Item> getAll() {
        return items.values();
    }

    /** Items dans le sac (non équipés). */
    public List<Item> getBag() {
        List<Item> bag = new ArrayList<>();
        for(Item i : items.values()) if(!i.isEquipped()) bag.add(i);
        return bag;
    }

    /** Item équipé dans un slot donné (null si vide). */
    public Item getEquipped(int position) {
        for(Item i : items.values()) if(i.getPosition() == position) return i;
        return null;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Ajoute un item (ou incrémente sa quantité si empilable et déjà présent).
     * Retourne l'item résultant (nouveau ou existant mis à jour).
     */
    public Item addItem(ItemTemplate template, int quantity) {
        // Tentative de stack pour les ressources/consommables (position -1, effets identiques)
        // TODO : vérifier que les effets sont bien identiques avant de stacker
        if(template.getTypeId() >= 48) { // ressources et consommables sont empilables
            for(Item existing : items.values()) {
                if(existing.getTemplate().getId() == template.getId() && !existing.isEquipped()) {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    return existing;
                }
            }
        }
        long uid  = UID_GEN.getAndIncrement();
        Item item = Item.create(uid, template, quantity, -1);
        items.put(uid, item);
        return item;
    }

    /**
     * Retire {@code quantity} exemplaires de l'item. Supprime si quantité tombe à 0.
     *
     * @return true si la suppression/réduction a réussi
     */
    public boolean removeItem(long uid, int quantity) {
        Item item = items.get(uid);
        if(item == null) return false;
        int newQty = item.getQuantity() - quantity;
        if(newQty <= 0) {
            items.remove(uid);
        } else {
            item.setQuantity(newQty);
        }
        return true;
    }

    /**
     * Équipe un item dans son slot par défaut (déduit du type du template).
     *
     * @return l'ancien item déséquipé depuis ce slot (null si slot était vide)
     */
    public Item equip(long uid) {
        Item item = items.get(uid);
        if(item == null) return null;
        int slot = defaultSlotFor(item.getTemplate().getTypeId());
        if(slot < 0) return null; // non équipable

        Item displaced = getEquipped(slot);
        if(displaced != null) displaced.setPosition(-1); // déséquipe le précédent

        item.setPosition(slot);
        return displaced;
    }

    /**
     * Déséquipe un item vers le sac.
     */
    public boolean unequip(long uid) {
        Item item = items.get(uid);
        if(item == null || !item.isEquipped()) return false;
        item.setPosition(-1);
        return true;
    }

    // ── Poids ─────────────────────────────────────────────────────────────────

    /** Poids total utilisé en grammes. */
    public int getUsedPods() {
        int total = 0;
        for(Item i : items.values()) total += i.getTemplate().getPods() * i.getQuantity();
        return total;
    }

    // ── Sérialisation protocole ───────────────────────────────────────────────

    /**
     * Paquet {@code OL} complet (liste au login).
     * Format : {@code OL<entry>|<entry>|...}
     */
    public String buildOLPacket() {
        if(items.isEmpty()) return "OL";
        StringBuilder sb = new StringBuilder("OL");
        boolean first = true;
        for(Item item : items.values()) {
            if(!first) sb.append('|');
            sb.append(item.toOLEntry());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Paquet {@code OA} — ajout d'un item côté client.
     */
    public static String buildOAPacket(Item item) {
        return "OA" + item.toOLEntry();
    }

    /**
     * Paquet {@code OM} — modification d'un item.
     */
    public static String buildOMPacket(Item item) {
        return "OM" + item.getUid() + "|" + item.toOLEntry();
    }

    /**
     * Paquet {@code OR} — suppression d'un item.
     */
    public static String buildORPacket(long uid) {
        return "OR" + uid;
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /**
     * Slot d'équipement par défaut selon le typeId du template.
     * Retourne -1 si non équipable.
     */
    private static int defaultSlotFor(int typeId) {
        switch(typeId) {
            case 1:  return 5;  // Amulette
            case 2:  return 6;  // Anneau (TODO : gérer 2 anneaux)
            case 3:  return 7;  // Ceinture
            case 4:  return 8;  // Bottes
            case 5:  return 1;  // Chapeau
            case 6:  return 2;  // Cape
            case 9:  return 9;  // Familier
            case 10: return 10; // Harnachement / Bouclier
            // Armes selon leur type — TODO : affiner selon soustype
            case 13: return 4;  // Épée
            case 14: return 4;  // Dague
            case 15: return 4;  // Bâton
            default: return -1; // ressource, consommable, non équipable
        }
    }

    /** Charge les items depuis la BDD dans cet inventaire. Voir {@link ItemsData#loadForCharacter}. */
    public void load(java.util.List<Item> loaded) {
        items.clear();
        for(Item item : loaded) items.put(item.getUid(), item);
    }
}
