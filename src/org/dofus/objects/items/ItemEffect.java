package org.dofus.objects.items;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Effet d'objet Dofus 1.29.
 *
 * Le protocole officiel utilise le format hexadecimal :
 * effectId#param1#param2#param3#diceText.
 * L'ancien format decimal interne reste accepte au chargement dans ItemsData.
 */
public class ItemEffect {

    private static final Pattern DICE_PATTERN = Pattern.compile("^(\\d+)d(\\d+)([+-]\\d+)?$");

    public static final int VITALITY  = 111;
    public static final int WISDOM    = 112;
    public static final int STRENGTH  = 118;
    public static final int AGILITY   = 119;
    public static final int CHANCE    = 123;
    public static final int INTEL     = 126;
    public static final int PODS_BONUS = 158;
    public static final int INITIATIVE = 174;
    public static final int PROSPECTING = 176;
    public static final int AP_BONUS  = 1001;
    public static final int MP_BONUS  = 1003;

    private final int effectId;
    private final int dice;
    private final int min;
    private final int max;
    private final int special;
    private final String specialText;

    public ItemEffect(int effectId, int dice, int min, int max, int special) {
        this(effectId, dice, min, max, Integer.toString(special));
    }

    public ItemEffect(int effectId, int dice, int min, int max, String specialText) {
        this.effectId = effectId;
        this.dice = dice;
        this.min = min;
        this.max = max;
        this.specialText = specialText != null ? specialText : "0";
        int parsedSpecial = 0;
        try {
            parsedSpecial = Integer.parseInt(this.specialText);
        } catch(NumberFormatException ignored) {
            // Dice expressions such as "1d6+4" are kept verbatim for the client.
        }
        this.special = parsedSpecial;
    }

    public static ItemEffect fixed(int effectId, int value) {
        return new ItemEffect(effectId, value, 0, 0, "0d0+" + value);
    }

    public static ItemEffect petLife(int current, int maximum) {
        int maxValue = Math.max(1, maximum);
        int currentValue = Math.max(0, Math.min(current, maxValue));
        return new ItemEffect(800, currentValue, 0, maxValue, "0");
    }

    /**
     * Corpulence familier Dofus 1.29.
     * Le client affiche 0 = normal, 7 = maigrichon/obese.
     * La valeur reelle est conservee dans le dice text pour pouvoir rattraper
     * exactement les repas en retard ou les repas donnes trop tot.
     */
    public static ItemEffect petBodyState(int corpulence) {
        int display = corpulence == 0 ? 0 : 7;
        int obeseFlag = corpulence > 0 ? display : 0;
        return new ItemEffect(806, display, obeseFlag, display, "0d0+" + corpulence);
    }

    public static ItemEffect param3(int effectId, int value) {
        return new ItemEffect(effectId, 0, 0, value, "0d0+" + value);
    }

    public static ItemEffect random(int effectId, int min, int max) {
        return new ItemEffect(effectId, min, max, 0, "1d" + Math.max(1, max - min + 1) + "+" + Math.max(0, min - 1));
    }

    public int getEffectId() { return effectId; }
    public int getDice() { return dice; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getSpecial() { return special; }
    public String getSpecialText() { return specialText; }

    public int getValue() {
        if(isRolledFixedValue()) {
            try { return Integer.parseInt(specialText.substring(4)); }
            catch(Exception ignored) { /* fallback below */ }
        }
        if(max > 0) return max;
        if(min > 0) return min;
        if(dice > 0) return dice;
        return special;
    }

    /**
     * Durabilité éthérée officielle côté client 1.29 :
     * ItemViewer lit param2 comme valeur courante et param3 comme maximum.
     * Les bases Ancestra stockent souvent 812#max#0#0#0d0+max, ce qui
     * laisse la barre à 0/0. Cette factory normalise en 812#0#cur#max#...
     */
    public static ItemEffect etherealDurability(int current, int maximum) {
        int maxValue = Math.max(0, maximum);
        int currentValue = Math.max(0, Math.min(current, maxValue));
        return new ItemEffect(812, 0, currentValue, maxValue, "0d0+" + maxValue);
    }

    public boolean isRolledFixedValue() {
        return min == 0 && max == 0 && specialText != null && specialText.startsWith("0d0+");
    }

    public boolean shouldRollInstance() {
        if(isRolledFixedValue()) return false;
        if(rollDiceText(specialText) != null) return true;
        return dice > 0 && min >= dice;
    }

    public int roll() {
        Integer diceTextRoll = rollDiceText(specialText);
        if(diceTextRoll != null) return diceTextRoll.intValue();

        // Format template frequent Dofus 1.29 : effect#min#max#0#diceText
        if(dice > 0 && min >= dice) return randomBetween(dice, min);

        // Format alternatif deja present dans certaines bases : effect#dice#min#max
        if(min > 0 && max >= min) return randomBetween(min, max);

        if(dice > 0) return dice;
        if(min > 0) return min;
        return max;
    }

    private static Integer rollDiceText(String text) {
        if(text == null) return null;
        String clean = text.trim();
        if(clean.isEmpty() || "0".equals(clean)) return null;

        Matcher m = DICE_PATTERN.matcher(clean);
        if(!m.matches()) return null;

        int count = Integer.parseInt(m.group(1));
        int sides = Integer.parseInt(m.group(2));
        int bonus = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;

        if(count <= 0 || sides <= 0) return null;

        int value = bonus;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for(int i = 0; i < count; i++) value += rnd.nextInt(1, sides + 1);
        return value;
    }

    private static int randomBetween(int a, int b) {
        int minValue = Math.min(a, b);
        int maxValue = Math.max(a, b);
        return ThreadLocalRandom.current().nextInt(minValue, maxValue + 1);
    }

    public ItemEffect rollInstance() {
        int value = roll();
        return fixed(effectId, value);
    }

    public ItemEffect maxInstance() {
        int value = maxRoll();
        return fixed(effectId, value);
    }

    public int maxRoll() {
        Integer diceTextMax = maxDiceText(specialText);
        if(diceTextMax != null) return diceTextMax.intValue();
        if(dice > 0 && min >= dice) return Math.max(dice, min);
        if(min > 0 && max >= min) return Math.max(min, max);
        if(dice > 0) return dice;
        if(min > 0) return min;
        return max;
    }

    private static Integer maxDiceText(String text) {
        if(text == null) return null;
        String clean = text.trim();
        if(clean.isEmpty() || "0".equals(clean)) return null;

        Matcher m = DICE_PATTERN.matcher(clean);
        if(!m.matches()) return null;

        int count = Integer.parseInt(m.group(1));
        int sides = Integer.parseInt(m.group(2));
        int bonus = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        if(count <= 0 || sides <= 0) return null;
        return count * sides + bonus;
    }

    public String toDofusString() {
        return Integer.toHexString(effectId)
            + "#" + Integer.toHexString(dice)
            + "#" + Integer.toHexString(min)
            + "#" + Integer.toHexString(max)
            + "#" + specialText;
    }
}
