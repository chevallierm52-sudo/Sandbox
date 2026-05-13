package org.dofus.objects.spells;

public class KnownSpell {

    private final int spellId;
    private int level;
    private int position;

    public KnownSpell(int spellId, int level, int position) {
        this.spellId = spellId;
        this.level = clampLevel(level);
        this.position = Math.max(0, position);
    }

    public int getSpellId() {
        return spellId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = clampLevel(level);
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = Math.max(0, position);
    }

    public int getBoostCost() {
        return level >= 6 ? Integer.MAX_VALUE : level;
    }

    private static int clampLevel(int level) {
        if(level < 1) return 1;
        if(level > 6) return 6;
        return level;
    }
}
