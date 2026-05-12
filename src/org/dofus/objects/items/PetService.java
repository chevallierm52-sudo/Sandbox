package org.dofus.objects.items;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.ItemsData;
import org.dofus.database.objects.PetsData;
import org.dofus.database.objects.PetsData.PetTemplate;
import org.dofus.objects.actors.Characters;
import org.dofus.utils.RegenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Familiers 1.29 : nourriture depuis pets.sql, points de vie, poudre d'Eniripsa
 * et transformation en fantome via DeadTemplate.
 */
public final class PetService {

    private static final Logger logger = LoggerFactory.getLogger(PetService.class);

    private static final int PET_TYPE = 18;
    private static final int PET_GHOST_TYPE = 90;
    private static final int PET_SLOT = 8;
    private static final int PET_LIFE = 800;
    private static final int PET_BODY_STATE = 806;
    private static final int PET_LAST_MEAL = 807;
    private static final int PET_LAST_MEAL_DATE = 808;
    private static final int ENIRIPSA_POWDER_TEMPLATE = 2239;

    private PetService() {}

    public static boolean feed(Characters character, IoSession session, Item pet, Item food) {
        if(character == null || session == null || pet == null || food == null) return false;
        if(isPetGhost(pet)) {
            session.write("Im153");
            return true;
        }
        if(!isPet(pet)) return false;

        if(food == pet || food.isEquipped()) {
            session.write("Im153");
            return true;
        }

        if(food.getTemplate().getId() == ENIRIPSA_POWDER_TEMPLATE) {
            return heal(character, session, pet, food);
        }

        PetTemplate petData = PetsData.get(pet.getTemplate().getId());
        int effectId = petData != null
                ? petData.effectForFood(food.getTemplate().getId(), food.getTemplate().getTypeId())
                : 0;
        if(effectId <= 0) {
            logger.debug("Pet feed refuse petUid={} petTpl={} foodUid={} foodTpl={}",
                    new Object[] { pet.getUid(), pet.getTemplate().getId(), food.getUid(), food.getTemplate().getId() });
            session.write("Im153");
            return true;
        }

        int life = getLife(pet);
        if(life <= 0) {
            transformToGhost(character, session, pet);
            return true;
        }

        FeedingWindow window = FeedingWindow.from(petData);
        long now = System.currentTimeMillis();
        int corpulence = refreshCorpulenceFromMissedMeals(pet, window, now);

        if(corpulence < 0) {
            // Familier maigrichon : on peut le gaver, chaque repas rattrape un repas manque.
            corpulence++;
            setCorpulence(pet, corpulence);
            stampMeal(pet, food, currentMealCount(pet), now);
            consumeFood(character, session, food);
            ItemsData.update(pet);
            refreshAfterPetChange(character, session, pet, false);
            session.write("Im029");
            return true;
        }

        if(wasFedTooSoon(pet, window, now)) {
            // Nourri trop tot : il grossit. Au-dela du premier gavage, il perd 1 PDV.
            corpulence++;
            setCorpulence(pet, corpulence);
            stampMeal(pet, food, currentMealCount(pet), now);
            consumeFood(character, session, food);
            if(corpulence > 1) {
                life = Math.max(0, life - 1);
                setLife(pet, life);
            }
            ItemsData.update(pet);
            if(life <= 0) {
                transformToGhost(character, session, pet);
                return true;
            }
            refreshAfterPetChange(character, session, pet, false);
            session.write(corpulence == 1 ? "Im026" : "Im027");
            return true;
        }

        int mealCount = nextMealCount(pet);
        boolean statChanged = false;
        if(mealCount >= 3) {
            statChanged = increasePetStat(pet, effectId, petData);
            mealCount = 0;
        }
        stampMeal(pet, food, mealCount, now);
        consumeFood(character, session, food);
        ItemsData.update(pet);

        refreshAfterPetChange(character, session, pet, statChanged);
        session.write("Im032");
        return true;
    }

    public static void onOwnerDeath(Characters character, IoSession session) {
        if(character == null || character.getInventory() == null) return;
        Item pet = character.getInventory().getEquipped(PET_SLOT);
        if(!isPet(pet)) return;

        int life = Math.max(0, getLife(pet) - 1);
        setLife(pet, life);

        if(life <= 0) {
            transformToGhost(character, session, pet);
            return;
        }

        ItemsData.update(pet);
        if(session != null && session.isConnected()) {
            session.write(Inventory.buildOCPacket(pet));
            session.write("Im025");
            RegenService.refresh(character, session);
        }
    }

    public static boolean isPet(Item item) {
        return item != null && item.getTemplate() != null && item.getTemplate().getTypeId() == PET_TYPE;
    }

    public static boolean isPetGhost(Item item) {
        return item != null && item.getTemplate() != null && item.getTemplate().getTypeId() == PET_GHOST_TYPE;
    }

    private static boolean heal(Characters character, IoSession session, Item pet, Item powder) {
        int life = getLife(pet);
        if(life <= 0) {
            session.write("Im119");
            return true;
        }

        int newLife = Math.min(maxLife(pet), life + 1);
        if(newLife == life) {
            session.write("Im026");
            return true;
        }

        setLife(pet, newLife);
        consumeFood(character, session, powder);
        ItemsData.update(pet);
        refreshAfterPetChange(character, session, pet, false);
        session.write("Im032");
        return true;
    }

    private static boolean increasePetStat(Item pet, int effectId, PetTemplate petData) {
        int maxWeight = petData != null && petData.getMax() > 0 ? petData.getMax() : petStatCap(pet.getTemplate(), effectId);
        if(maxWeight <= 0) return false;

        int gain = petData != null && petData.getGain() > 0 ? petData.getGain() : 1;
        int weight = petStatWeight(effectId);
        int currentWeight = currentPetStatWeight(pet);
        int allowedGain = Math.min(gain, Math.max(0, (maxWeight - currentWeight) / Math.max(1, weight)));
        if(allowedGain <= 0) return false;

        int current = currentPetStat(pet, effectId);
        pet.replaceEffect(ItemEffect.fixed(effectId, current + allowedGain));
        return true;
    }

    private static int currentPetStatWeight(Item pet) {
        int total = 0;
        for(ItemEffect effect : pet.getRolledEffects()) {
            int id = effect.getEffectId();
            if(id == PET_LIFE || id == PET_BODY_STATE || id == PET_LAST_MEAL || id == PET_LAST_MEAL_DATE) continue;
            if(id == 940 || id == 717) continue;
            int value = Math.max(0, effect.getValue());
            total += petStatWeight(id) * value;
        }
        return total;
    }

    private static int petStatWeight(int effectId) {
        // Barème officiel 1.29 : résists = 4, %dommages = 2, sagesse/soins/dommages = lourds.
        if(effectId == 0x8a || effectId == 0x7c) return 2;
        if(effectId == 0xd2 || effectId == 0xd3 || effectId == 0xd4 || effectId == 0xd5 || effectId == 0xd6) return 4;
        if(effectId == 0xb2 || effectId == 0x70) return 8;
        return 1;
    }

    private static int currentPetStat(Item pet, int effectId) {
        int value = pet.getEffectValue(effectId);
        return value == Integer.MIN_VALUE ? 0 : Math.max(0, value);
    }

    private static int petStatCap(ItemTemplate template, int effectId) {
        if(template == null) return 0;
        int cap = 0;
        for(ItemEffect effect : template.getEffects()) {
            if(effect.getEffectId() != effectId) continue;
            cap = Math.max(cap, Math.max(effect.getDice(), Math.max(effect.getMin(), Math.max(effect.getMax(), effect.getValue()))));
        }
        return cap;
    }

    private static void transformToGhost(Characters character, IoSession session, Item pet) {
        PetTemplate petData = PetsData.get(pet.getTemplate().getId());
        int deadTemplateId = petData != null ? petData.getDeadTemplateId() : 0;
        ItemTemplate deadTemplate = deadTemplateId > 0 ? ItemsData.getTemplate(deadTemplateId) : null;
        if(deadTemplate == null) {
            setLife(pet, 0);
            ItemsData.update(pet);
            if(session != null && session.isConnected()) session.write("Im154");
            return;
        }

        boolean wasEquipped = pet.isEquipped();
        if(wasEquipped) character.getInventory().unequip(pet.getUid());

        List<ItemEffect> kept = new ArrayList<ItemEffect>(pet.getRolledEffects());
        pet.setTemplate(deadTemplate);
        pet.setQuantity(1);
        pet.setPosition(-1);
        pet.removeEffect(PET_LIFE);
        pet.removeEffect(PET_BODY_STATE);
        pet.removeEffect(PET_LAST_MEAL);
        pet.removeEffect(PET_LAST_MEAL_DATE);
        for(ItemEffect effect : kept) {
            int id = effect.getEffectId();
            if(id != PET_LIFE && id != PET_BODY_STATE && id != PET_LAST_MEAL && id != PET_LAST_MEAL_DATE) {
                pet.replaceEffect(effect);
            }
        }

        ItemsData.update(pet);
        if(session != null && session.isConnected()) {
            if(wasEquipped) session.write(Inventory.buildOMPacket(pet));
            session.write(Inventory.buildOCPacket(pet));
            session.write("Im154");
            session.write("Ow" + character.getInventory().getUsedPods() + "|" + character.getMaxPods());
            RegenService.refresh(character, session);
        }
    }

    private static void consumeFood(Characters character, IoSession session, Item food) {
        Inventory inv = character.getInventory();
        long foodUid = food.getUid();

        // Le drag client OM{nourriture}|8 peut concerner une pile. Officiel 1.29 :
        // un nourrissage = une seule ressource consommee. On force donc la pile a rester
        // dans le sac cote client, puis on met seulement la quantite a jour.
        food.setPosition(-1);
        inv.removeItem(foodUid, 1);
        Item remainingFood = inv.getByUid(foodUid);
        if(remainingFood == null) {
            ItemsData.delete(foodUid);
            session.write(Inventory.buildORPacket(foodUid));
        } else {
            remainingFood.setPosition(-1);
            ItemsData.update(remainingFood);
            session.write(Inventory.buildOMPacket(remainingFood));
            session.write(Inventory.buildOQPacket(remainingFood));
        }
    }

    private static void refreshAfterPetChange(Characters character, IoSession session, Item pet, boolean statChanged) {
        session.write(Inventory.buildOCPacket(pet));
        session.write("Ow" + character.getInventory().getUsedPods() + "|" + character.getMaxPods());
        if(pet.isEquipped() && statChanged) {
            RegenService.refresh(character, session);
        }
    }

    private static boolean wasFedTooSoon(Item pet, FeedingWindow window, long now) {
        if(window.minMillis <= 0) return false;
        long lastMeal = getLastMealMillis(pet);
        return lastMeal > 0 && lastMeal + window.minMillis > now;
    }

    private static int refreshCorpulenceFromMissedMeals(Item pet, FeedingWindow window, long now) {
        int corpulence = getCorpulence(pet);
        if(window.maxMillis <= 0) return corpulence;

        long lastMeal = getLastMealMillis(pet);
        if(lastMeal <= 0 || lastMeal + window.maxMillis >= now) return corpulence;

        int missedMeals = (int) Math.max(1L, Math.min(100L, (now - lastMeal) / window.maxMillis));
        corpulence -= missedMeals;
        setCorpulence(pet, corpulence);
        if(corpulence < 0) resetMealCount(pet);
        return corpulence;
    }

    private static int getCorpulence(Item pet) {
        ItemEffect effect = pet.getEffect(PET_BODY_STATE);
        if(effect == null) return 0;

        String text = effect.getSpecialText();
        if(text != null && text.startsWith("0d0+")) {
            try { return Integer.parseInt(text.substring(4)); }
            catch(NumberFormatException ignored) {}
        }

        if(effect.getDice() == 7 && effect.getMin() == 0) return -1;
        if(effect.getDice() == 7 && effect.getMin() > 0) return 1;
        return 0;
    }

    private static void setCorpulence(Item pet, int corpulence) {
        pet.replaceEffect(ItemEffect.petBodyState(Math.max(-100, Math.min(100, corpulence))));
    }

    private static void resetMealCount(Item pet) {
        ItemEffect current = pet.getEffect(PET_LAST_MEAL);
        int lastFood = current != null ? Math.max(0, current.getMax()) : 0;
        pet.replaceEffect(new ItemEffect(PET_LAST_MEAL, 0, 0, lastFood, "0d0+" + lastFood));
    }

    private static long getLastMealMillis(Item pet) {
        ItemEffect effect = pet.getEffect(PET_LAST_MEAL_DATE);
        if(effect == null) return 0L;
        try {
            int year = effect.getDice();
            int monthDay = effect.getMin();
            int hourMinute = effect.getMax();
            if(year <= 0 || monthDay <= 0) return 0L;
            int month = monthDay / 100 + 1;
            int day = monthDay % 100;
            int hour = hourMinute / 100;
            int minute = hourMinute % 100;
            LocalDateTime date = LocalDateTime.of(year, month, day, hour, minute);
            return date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch(Exception ignored) {
            return 0L;
        }
    }

    private static int currentMealCount(Item pet) {
        ItemEffect lastMeal = pet.getEffect(PET_LAST_MEAL);
        return lastMeal != null ? Math.max(0, Math.min(2, lastMeal.getMin())) : 0;
    }

    private static int nextMealCount(Item pet) {
        return currentMealCount(pet) + 1;
    }

    private static void stampMeal(Item pet, Item food, int mealCount, long nowMillis) {
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault());
        int monthDay = (now.getMonthValue() - 1) * 100 + now.getDayOfMonth();
        int hourMinute = now.getHour() * 100 + now.getMinute();
        pet.replaceEffect(new ItemEffect(PET_LAST_MEAL_DATE, now.getYear(), monthDay, hourMinute, "0d0+" + hourMinute));
        // 807 garde la derniere nourriture en param3 et le nombre de repas valides en param2.
        pet.replaceEffect(new ItemEffect(PET_LAST_MEAL, 0, Math.max(0, Math.min(2, mealCount)), food.getTemplate().getId(), "0d0+" + food.getTemplate().getId()));
    }

    private static int getLife(Item pet) {
        ItemEffect effect = pet.getEffect(PET_LIFE);
        if(effect == null) return maxLife(pet);

        int life = effect.getDice() > 0 ? effect.getDice() : effect.getValue();
        return Math.max(0, life);
    }

    private static void setLife(Item pet, int life) {
        ItemEffect current = pet.getEffect(PET_LIFE);
        int maximum = current != null && current.getMax() > 0 ? current.getMax() : maxLife(pet);
        pet.replaceEffect(ItemEffect.petLife(Math.max(0, life), maximum));
    }

    private static int maxLife(Item pet) {
        return 10;
    }

    private static final class FeedingWindow {
        private final long minMillis;
        private final long maxMillis;

        private FeedingWindow(long minMillis, long maxMillis) {
            this.minMillis = minMillis;
            this.maxMillis = maxMillis;
        }

        private static FeedingWindow from(PetTemplate petData) {
            if(petData == null || petData.getGap() == null || petData.getGap().trim().isEmpty()) {
                return new FeedingWindow(0L, 0L);
            }
            String[] split = petData.getGap().split("[,;|]");
            int minHours = parseHour(split, 0);
            int maxHours = parseHour(split, 1);
            return new FeedingWindow(hoursToMillis(minHours), hoursToMillis(maxHours));
        }

        private static int parseHour(String[] split, int index) {
            if(split == null || index >= split.length) return 0;
            try { return Math.max(0, Integer.parseInt(split[index].trim())); }
            catch(Exception ignored) { return 0; }
        }

        private static long hoursToMillis(int hours) {
            return hours <= 0 ? 0L : hours * 3600000L;
        }
    }
}
