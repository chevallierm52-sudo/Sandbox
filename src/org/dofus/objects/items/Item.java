package org.dofus.objects.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Instance d'objet appartenant à un personnage.
 *
 * Un objet a un template (le modèle global) et des effets "rollés" propres à
 * cette instance (générés au craft ou au drop). Les effets peuvent être
 * différents des effets du template (fourchette min-max).
 *
 * Position dans l'inventaire :
 *   -1 = non équipé (sac)
 *    1 = chapeau
 *    2 = cape
 *    3 = amulette
 *    4 = arme
 *    5 = anneau gauche
 *    6 = ceinture
 *    7 = anneau droit
 *    8 = bottes
 *    9 = animal de compagnie
 *   10 = arme harnachement / secondaire
 */
public class Item {

    private final long         uid;           // ID unique de cette instance en BDD
    private final ItemTemplate template;
    private       int          quantity;
    private       int          position;      // slot inventaire (-1 = sac)
    private final List<ItemEffect> rolledEffects; // effets propres à cette instance

    public Item(long uid, ItemTemplate template, int quantity, int position,
                List<ItemEffect> rolledEffects) {
        this.uid          = uid;
        this.template     = template;
        this.quantity     = quantity;
        this.position     = position;
        this.rolledEffects = rolledEffects != null
            ? new ArrayList<>(rolledEffects)
            : rollFromTemplate(template);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Crée un item avec des effets fraîchement rollés depuis le template.
     */
    public static Item create(long uid, ItemTemplate template, int quantity, int position) {
        return new Item(uid, template, quantity, position, null);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public long         getUid()      { return uid;      }
    public ItemTemplate getTemplate() { return template; }
    public int          getQuantity() { return quantity; }
    public void         setQuantity(int q) { this.quantity = q; }
    public int          getPosition() { return position; }
    public void         setPosition(int p) { this.position = p; }
    public List<ItemEffect> getRolledEffects() { return Collections.unmodifiableList(rolledEffects); }

    public boolean isEquipped() { return position >= 1; }

    // ── Protocole Dofus ───────────────────────────────────────────────────────

    /**
     * Sérialise l'item pour le paquet {@code OL} (liste objets inventaire).
     * Format : {@code uid~templateId~qty~position~effectId,d,min,max,s#...}
     */
    public String toOLEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append(uid)            .append('~')
          .append(template.getId()).append('~')
          .append(quantity)        .append('~')
          .append(position)        .append('~');
        // Effets rollés
        boolean first = true;
        for(ItemEffect e : rolledEffects) {
            if(!first) sb.append('#');
            sb.append(e.getEffectId()).append(',')
              .append(e.getDice())    .append(',')
              .append(e.getMin())     .append(',')
              .append(e.getMax())     .append(',')
              .append(e.getSpecial());
            first = false;
        }
        return sb.toString();
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Génère des effets rollés depuis les fourchettes du template. */
    private static List<ItemEffect> rollFromTemplate(ItemTemplate template) {
        List<ItemEffect> result = new ArrayList<>();
        for(ItemEffect e : template.getEffects()) {
            result.add(new ItemEffect(e.getEffectId(), e.getDice(),
                e.roll(), e.getMax(), e.getSpecial()));
        }
        return result;
    }

    @Override
    public String toString() {
        return "Item{uid=" + uid + ", tpl=" + template.getId()
            + " '" + template.getName() + "', qty=" + quantity
            + ", pos=" + position + "}";
    }
}
