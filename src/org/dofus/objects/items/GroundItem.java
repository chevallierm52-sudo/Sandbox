package org.dofus.objects.items;

/**
 * Objet depose au sol sur une carte.
 *
 * Le client 1.29 n'a besoin que de la cellule et du templateId pour afficher
 * l'objet au sol via GDO. L'instance complete est gardee cote serveur pour
 * preparer la future phase ramassage/persistence.
 */
public class GroundItem {

    private final long id;
    private final int mapId;
    private final short cellId;
    private final Item item;
    private final long droppedAt;

    public GroundItem(long id, int mapId, short cellId, Item item) {
        this.id = id;
        this.mapId = mapId;
        this.cellId = cellId;
        this.item = item;
        this.droppedAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public int getMapId() { return mapId; }
    public short getCellId() { return cellId; }
    public Item getItem() { return item; }
    public long getDroppedAt() { return droppedAt; }

    public int getTemplateId() {
        return item.getTemplate().getId();
    }

    public int getQuantity() {
        return item.getQuantity();
    }

    public String toGDOPacket() {
        return "GDO+" + cellId + ";" + getTemplateId() + ";0";
    }

    public String toGDORemovePacket() {
        return "GDO-" + cellId;
    }
}
