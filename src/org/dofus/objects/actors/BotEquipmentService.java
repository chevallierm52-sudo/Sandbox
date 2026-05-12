package org.dofus.objects.actors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dofus.database.objects.ItemsData;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemEffect;
import org.dofus.objects.items.ItemTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Equipement automatique des bots.
 *
 * Objectif : donner aux bots une silhouette et des stats cohérentes avec leur
 * niveau et leur classe, sans créer de table SQL dédiée et sans persister ces
 * objets dans character_items, puisque les bots sont temporaires.
 *
 * Cette version tient compte des armes de prédilection Dofus 1.x/Rétro :
 * - arme principale : coefficient 100%
 * - arme secondaire : coefficient 95%
 * - autres armes : coefficient 90%
 * - Sacrieur : pas de prédilection, donc 90% partout
 */
public final class BotEquipmentService {

    private static final Logger logger = LoggerFactory.getLogger(BotEquipmentService.class);

    private static final int TYPE_AMULETTE = 1;
    private static final int TYPE_ARC = 2;
    private static final int TYPE_BAGUETTE = 3;
    private static final int TYPE_BATON = 4;
    private static final int TYPE_DAGUES = 5;
    private static final int TYPE_EPEE = 6;
    private static final int TYPE_MARTEAU = 7;
    private static final int TYPE_PELLE = 8;
    private static final int TYPE_ANNEAU = 9;
    private static final int TYPE_CEINTURE = 10;
    private static final int TYPE_BOTTES = 11;
    private static final int TYPE_COIFFE = 16;
    private static final int TYPE_CAPE = 17;
    private static final int TYPE_FAMILIER = 18;
    private static final int TYPE_HACHE = 19;
    private static final int TYPE_BOUCLIER = 82;

    private static final int[] CORE_TYPES = {
        TYPE_COIFFE,
        TYPE_CAPE,
        TYPE_AMULETTE,
        TYPE_ANNEAU,
        TYPE_ANNEAU,
        TYPE_CEINTURE,
        TYPE_BOTTES,
        TYPE_FAMILIER,
        TYPE_BOUCLIER
    };

    private static final int[] WEAPON_TYPES = {
        TYPE_ARC,
        TYPE_BAGUETTE,
        TYPE_BATON,
        TYPE_DAGUES,
        TYPE_EPEE,
        TYPE_MARTEAU,
        TYPE_PELLE,
        TYPE_HACHE
    };

    private BotEquipmentService() {}

    public static void equipForLevelAndBreed(Characters bot) {
        if(bot == null || bot.getExperience() == null || bot.getInventory() == null) return;

        int level = Math.max(1, bot.getExperience().getLevel());
        byte breedId = bot.getBreedId();
        Set<Integer> usedTemplates = new HashSet<Integer>();

        for(int typeId : CORE_TYPES) {
            ItemTemplate template = bestTemplate(typeId, level, breedId, usedTemplates);
            equip(bot, template, usedTemplates);
        }

        ItemTemplate weapon = bestWeapon(level, breedId, usedTemplates);
        equip(bot, weapon, usedTemplates);

        logger.debug("Bot {} équipé automatiquement pour lvl={} breed={}",
            new Object[] { bot.getName(), level, breedId });
    }

    private static void equip(Characters bot, ItemTemplate template, Set<Integer> usedTemplates) {
        if(template == null) return;
        Inventory inv = bot.getInventory();
        Item item = inv.addItem(template, 1);
        Item displaced = inv.equip(bot, item.getUid());
        if(item.getPosition() >= 0) {
            usedTemplates.add(template.getId());
        } else {
            inv.removeItem(item.getUid(), 1);
        }
        if(displaced != null) displaced.setPosition(-1);
    }

    private static ItemTemplate bestWeapon(int level, byte breedId, Set<Integer> usedTemplates) {
        ItemTemplate best = null;

        // On tente d'abord les armes officiellement avantagées pour la classe.
        for(int typeId : preferredWeaponTypes(breedId)) {
            ItemTemplate candidate = bestTemplate(typeId, level, breedId, usedTemplates);
            if(candidate != null && (best == null || score(candidate, breedId, level) > score(best, breedId, level))) {
                best = candidate;
            }
        }
        if(best != null) return best;

        // Fallback : n'importe quelle arme exploitable, avec malus de prédilection appliqué au score.
        for(int typeId : WEAPON_TYPES) {
            ItemTemplate candidate = bestTemplate(typeId, level, breedId, usedTemplates);
            if(candidate != null && (best == null || score(candidate, breedId, level) > score(best, breedId, level))) {
                best = candidate;
            }
        }
        return best;
    }

    private static ItemTemplate bestTemplate(int typeId, int level, byte breedId, Set<Integer> usedTemplates) {
        Collection<ItemTemplate> all = ItemsData.getTemplates().values();
        List<ItemTemplate> candidates = new ArrayList<ItemTemplate>();
        int minLevel = Math.max(1, level - 25);

        for(ItemTemplate template : all) {
            if(template == null) continue;
            if(template.getTypeId() != typeId) continue;
            if(template.getLevel() > level) continue;
            if(template.getLevel() < minLevel && level > 30) continue;
            if(usedTemplates.contains(template.getId())) continue;
            if(template.getName() == null || template.getName().trim().isEmpty()) continue;
            candidates.add(template);
        }

        if(candidates.isEmpty()) {
            for(ItemTemplate template : all) {
                if(template == null) continue;
                if(template.getTypeId() != typeId) continue;
                if(template.getLevel() > level) continue;
                if(usedTemplates.contains(template.getId())) continue;
                candidates.add(template);
            }
        }

        if(candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingInt((ItemTemplate t) -> score(t, breedId, level)).reversed());
        return candidates.get(0);
    }

    private static int score(ItemTemplate template, byte breedId, int botLevel) {
        int score = template.getLevel() * 4;
        int distance = Math.abs(botLevel - template.getLevel());
        score -= distance * 2;

        for(ItemEffect effect : template.getEffects()) {
            int value = Math.max(effect.getMax(), Math.max(effect.getMin(), effect.getDice()));
            if(value <= 0) value = 1;
            score += effectWeight(effect.getEffectId(), breedId) * value;
        }

        if(isWeaponType(template.getTypeId())) {
            score = score * weaponPredilectionPercent(breedId, template.getTypeId()) / 100;
            score += weaponPreferenceBonus(breedId, template.getTypeId());
        }

        return score;
    }

    private static int effectWeight(int effectId, byte breedId) {
        // Effets communs utiles à tout le monde.
        if(effectId == 111) return 4;   // vitalité
        if(effectId == 112) return 2;   // sagesse
        if(effectId == 138) return 2;   // dommages %
        if(effectId == 142) return 2;   // dommages fixes
        if(effectId == 158) return 1;   // pods
        if(effectId == 174) return 1;   // initiative
        if(effectId == 176) return 2;   // prospection
        if(effectId == 178) return 2;   // soins
        if(effectId == 182) return 2;   // invocations
        if(effectId == 1001) return 30; // PA
        if(effectId == 1003) return 25; // PM

        switch(breedId) {
            case 1:  return weight(effectId, 126, 9, 118, 4, 111, 4, 112, 2); // Féca : int, force, vita
            case 2:  return weight(effectId, 126, 8, 111, 6, 182, 6, 112, 2); // Osa : int/vita/invoc
            case 3:  return weight(effectId, 126, 9, 178, 7, 112, 4, 111, 3); // Eni : int/soins/sagesse
            case 4:  return weight(effectId, 119, 8, 118, 7, 142, 4, 111, 3); // Sram : agi/force/dommages
            case 5:  return weight(effectId, 126, 8, 112, 6, 118, 3, 111, 3); // Xélor : int/sagesse
            case 6:  return weight(effectId, 118, 7, 119, 6, 126, 4, 123, 4); // Eca : force/agi, multi possible
            case 7:  return weight(effectId, 118, 10, 111, 4, 142, 4, 119, 3); // Iop : force/dommages
            case 8:  return weight(effectId, 119, 7, 118, 6, 126, 6, 142, 3); // Cra : agi/force/int
            case 9:  return weight(effectId, 111, 11, 118, 5, 119, 5, 126, 4); // Sacri : vita puis éléments
            case 10: return weight(effectId, 123, 9, 176, 6, 112, 4, 111, 3); // Enu : chance/PP/sagesse
            case 11: return weight(effectId, 118, 8, 126, 6, 111, 4, 112, 2); // Sadi : force/int
            case 12: return weight(effectId, 123, 7, 118, 6, 119, 5, 126, 5); // Panda : multi, chance/force
            default: return effectId == 111 ? 4 : 0;
        }
    }

    private static int weight(int effectId, int e1, int w1, int e2, int w2, int e3, int w3, int e4, int w4) {
        if(effectId == e1) return w1;
        if(effectId == e2) return w2;
        if(effectId == e3) return w3;
        if(effectId == e4) return w4;
        return 0;
    }

    private static boolean isWeaponType(int typeId) {
        for(int t : WEAPON_TYPES) {
            if(t == typeId) return true;
        }
        return false;
    }

    /**
     * Coefficient officiel des armes de prédilection : 100, 95 ou 90.
     * Le Sacrieur n'a pas d'arme de prédilection en 1.x/Rétro.
     */
    public static int weaponPredilectionPercent(byte breedId, int weaponTypeId) {
        int[] preferred = preferredWeaponTypes(breedId);
        if(preferred.length > 0 && preferred[0] == weaponTypeId) return 100;
        if(preferred.length > 1 && preferred[1] == weaponTypeId) return 95;
        return 90;
    }

    /**
     * Bonus de score volontairement léger : le coefficient 100/95/90 fait déjà le gros du tri,
     * mais ce bonus évite qu'une arme hors-classe avec quelques stats random passe devant.
     */
    private static int weaponPreferenceBonus(byte breedId, int weaponTypeId) {
        int[] preferred = preferredWeaponTypes(breedId);
        if(preferred.length > 0 && preferred[0] == weaponTypeId) return 250;
        if(preferred.length > 1 && preferred[1] == weaponTypeId) return 125;
        return 0;
    }

    /**
     * Types d'armes Dofus 1.x/Rétro :
     * 2 arc, 3 baguette, 4 bâton, 5 dagues, 6 épée, 7 marteau, 8 pelle, 19 hache.
     */
    private static int[] preferredWeaponTypes(byte breedId) {
        switch(breedId) {
            case 1:  return new int[] { TYPE_BATON, TYPE_BAGUETTE };     // Féca : bâton 100%, baguette 95%
            case 2:  return new int[] { TYPE_MARTEAU, TYPE_BATON };      // Osamodas : marteau 100%, bâton 95%
            case 3:  return new int[] { TYPE_BAGUETTE, TYPE_BATON };     // Eniripsa : baguette 100%, bâton 95%
            case 4:  return new int[] { TYPE_DAGUES, TYPE_ARC };         // Sram : dagues 100%, arc 95%
            case 5:  return new int[] { TYPE_MARTEAU, TYPE_BAGUETTE };   // Xélor : marteau 100%, baguette 95%
            case 6:  return new int[] { TYPE_EPEE, TYPE_DAGUES };        // Ecaflip : épée 100%, dagues 95%
            case 7:  return new int[] { TYPE_EPEE, TYPE_MARTEAU };       // Iop : épée 100%, marteau 95%
            case 8:  return new int[] { TYPE_ARC, TYPE_DAGUES };         // Crâ : arc 100%, dagues 95%
            case 9:  return new int[] { };                               // Sacrieur : aucune prédilection
            case 10: return new int[] { TYPE_PELLE, TYPE_MARTEAU };      // Enutrof : pelle 100%, marteau 95%
            case 11: return new int[] { TYPE_BATON, TYPE_BAGUETTE };     // Sadida : bâton 100%, baguette 95%
            case 12: return new int[] { TYPE_HACHE, TYPE_BATON };        // Pandawa : hache 100%, bâton 95%
            default: return WEAPON_TYPES;
        }
    }
}
