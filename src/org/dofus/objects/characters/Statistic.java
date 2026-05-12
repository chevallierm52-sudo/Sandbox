package org.dofus.objects.characters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.constants.EConstants;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemEffect;

public class Statistic {

	public static final int BASE_ACTION_POINTS = 6;
	public static final int BASE_MOVEMENT_POINTS = 3;
	public static final int LEVEL_100_ACTION_POINT_BONUS = 1;

	private final ConcurrentMap<Integer, Integer> statistics;

	//Loading character
	public Statistic(ConcurrentMap<Integer, Integer> stats) {
		this.statistics = stats != null ? stats : new ConcurrentHashMap<Integer, Integer>();
	}
	
	//Create
	public Statistic(Characters character) {
		this.statistics = new ConcurrentHashMap<Integer, Integer>();
		statistics.put(EConstants.ADD_STRENGTH.getInt(), 0);
		statistics.put(EConstants.ADD_WISDOM.getInt(), 0);
		statistics.put(EConstants.ADD_INTELLIGENCE.getInt(), 0);
		statistics.put(EConstants.ADD_CHANCE.getInt(), 0);
		statistics.put(EConstants.ADD_AGILITY.getInt(), 0);
		 
		statistics.put(EConstants.ADD_AP.getInt(), BASE_ACTION_POINTS);
		statistics.put(EConstants.ADD_MP.getInt(), BASE_MOVEMENT_POINTS);
		statistics.put(EConstants.ADD_PROSPECTION.getInt(), character.getBreed().getProspecting());
		statistics.put(EConstants.ADD_WEIGHT.getInt(), 1000);
		statistics.put(EConstants.ADD_SUMMONS.getInt(), 1);
		statistics.put(EConstants.ADD_INITIATIVE.getInt(), 1);
	}

	public void ensureDefaults(Characters character) {
		if(character == null || character.getBreed() == null) return;
		statistics.putIfAbsent(EConstants.ADD_STRENGTH.getInt(), 0);
		statistics.putIfAbsent(EConstants.ADD_VITALITY.getInt(), 0);
		statistics.putIfAbsent(EConstants.ADD_WISDOM.getInt(), 0);
		statistics.putIfAbsent(EConstants.ADD_INTELLIGENCE.getInt(), 0);
		statistics.putIfAbsent(EConstants.ADD_CHANCE.getInt(), 0);
		statistics.putIfAbsent(EConstants.ADD_AGILITY.getInt(), 0);
		ensureMinimum(EConstants.ADD_AP.getInt(), BASE_ACTION_POINTS);
		ensureMinimum(EConstants.ADD_MP.getInt(), BASE_MOVEMENT_POINTS);
		statistics.putIfAbsent(EConstants.ADD_PROSPECTION.getInt(), character.getBreed().getProspecting());
		statistics.putIfAbsent(EConstants.ADD_WEIGHT.getInt(), 1000);
		statistics.putIfAbsent(EConstants.ADD_SUMMONS.getInt(), 1);
		statistics.putIfAbsent(EConstants.ADD_INITIATIVE.getInt(), 1);
	}

	private void ensureMinimum(int effectId, int minimum) {
		Integer current = statistics.get(effectId);
		if(current == null || current < minimum) statistics.put(effectId, minimum);
	}
	
	public ConcurrentMap<Integer, Integer> getStatistics() {
		return statistics;
	}
	
	public int getEffect(int id) {
		if(statistics.get(id) != null)
			return statistics.get(id);
		else
			return 0;
	}
	
	public int add(int id, int value) {
		Integer current = statistics.get(id);
		int next = (current == null || current == 0) ? value : current + value;
		statistics.put(id, next);
		return next;
	}


    /**
     * Somme des bonus/malus fournis par les objets equipes du personnage.
     * On ne modifie pas les stats de base : le bonus reste separe dans la
     * colonne equipement du paquet As, comme le client 1.29 l'attend.
     */
    public static int equipmentBonus(Characters character, int effectId) {
        if(character == null || character.getInventory() == null) return 0;
        int total = 0;
        for(Item item : character.getInventory().getAll()) {
            if(item == null || !item.isEquipped()) continue;
            for(ItemEffect effect : item.getRolledEffects()) {
                total += equipmentContribution(effect, effectId);
            }
        }
        return total;
    }

    public static int totalWithEquipment(Characters character, int effectId) {
        int base = baseValue(character, effectId);
        return base + equipmentBonus(character, effectId);
    }

    private static int baseValue(Characters character, int effectId) {
        int base = character != null && character.getStats() != null ? character.getStats().getEffect(effectId) : 0;
        if(character != null && effectId == EConstants.ADD_AP.getInt()
                && character.getExperience() != null) {
            int level = character.getExperience().getLevel();
            if(level >= 100) base += LEVEL_100_ACTION_POINT_BONUS;
        }
        return base;
    }

    private static int equipmentContribution(ItemEffect effect, int wantedEffectId) {
        if(effect == null) return 0;
        int effectId = effect.getEffectId();
        int value = effectValue(effect);

        if(effectId == wantedEffectId) return value;

        // Malus officiels Dofus 1.29 : on les convertit en negatif sur la stat cible.
        if(wantedEffectId == EConstants.ADD_AP.getInt() && effectId == EConstants.REMOVE_AP.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_MP.getInt() && effectId == EConstants.REMOVE_MP.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_RANGE.getInt() && effectId == EConstants.REDUCE_RANGE.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_INITIATIVE.getInt() && effectId == EConstants.REMOVE_INITIATIVE.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_PROSPECTION.getInt() && effectId == EConstants.REMOVE_PROSPECTION.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_VITALITY.getInt() && effectId == EConstants.REMOVE_VITALITY.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_WISDOM.getInt() && effectId == EConstants.REMOVE_WISDOM.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_STRENGTH.getInt() && effectId == EConstants.REMOVE_STRENGTH.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_INTELLIGENCE.getInt() && effectId == EConstants.REMOVE_INTELLIGENCE.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_CHANCE.getInt() && effectId == EConstants.REMOVE_CHANCE.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_AGILITY.getInt() && effectId == EConstants.REMOVE_AGILITY.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_WEIGHT.getInt() && effectId == EConstants.REMOVE_WEIGHT.getInt()) return -value;
        if(wantedEffectId == EConstants.ADD_CRITICAL_HIT.getInt() && effectId == EConstants.REMOVE_CRITICAL_HIT.getInt()) return -value;

        return 0;
    }

    private static int effectValue(ItemEffect effect) {
        if(effect.getDice() != 0) return Math.abs(effect.getDice());
        if(effect.getMin() != 0) return Math.abs(effect.getMin());
        if(effect.getMax() != 0) return Math.abs(effect.getMax());
        if(effect.getSpecial() != 0) return Math.abs(effect.getSpecial());
        return 0;
    }

    private static void appendStatLine(StringBuilder sb, Characters character, int effectId) {
        int base = baseValue(character, effectId);
        int equip = equipmentBonus(character, effectId);
        int gift = 0;
        int buff = 0;
        int total = base + equip + gift + buff;
        sb.append(base).append(',')
          .append(equip).append(',')
          .append(gift).append(',')
          .append(buff).append(',')
          .append(total).append('|');
    }

	public static String statisticsMessage(Characters characters, long currentExperience, 
			long minExp, long maxExp, long kamas, short statsPoints, short spellsPoints, 
			int alignId, short alignLevel, short alignGrade, long honor, int dishonor, boolean pvpEnabled, 
			short lifePoints, short maxLifePoints, short energy, short maxEnergy, int initiative, int prospection) {
	
		StringBuilder sb = new StringBuilder(500).append("As");
	
		sb.append(currentExperience).append(',').append(minExp).append(',').append(maxExp).append('|');
		sb.append(kamas).append('|');
		sb.append(statsPoints).append('|').append(spellsPoints).append('|');
		sb.append(alignId).append('~').append(alignId).append(',').append(alignLevel).append(',').append(alignGrade);
		sb.append(',').append(honor).append(',').append(dishonor).append(',').append(pvpEnabled ? "1|" : "0|");
		sb.append(lifePoints).append(',').append(maxLifePoints).append('|');
		sb.append(energy).append(',').append(maxEnergy).append('|');
		sb.append(initiative).append('|').append(prospection).append('|');
	
		// Stats envoyees au format base,equipement,gift,buff,total.
		appendStatLine(sb, characters, EConstants.ADD_AP.getInt());
		appendStatLine(sb, characters, EConstants.ADD_MP.getInt());
		appendStatLine(sb, characters, EConstants.ADD_STRENGTH.getInt());
		appendStatLine(sb, characters, EConstants.ADD_VITALITY.getInt());
		appendStatLine(sb, characters, EConstants.ADD_WISDOM.getInt());
		appendStatLine(sb, characters, EConstants.ADD_CHANCE.getInt());
		appendStatLine(sb, characters, EConstants.ADD_AGILITY.getInt());
		appendStatLine(sb, characters, EConstants.ADD_INTELLIGENCE.getInt());
		appendStatLine(sb, characters, EConstants.ADD_RANGE.getInt());
		appendStatLine(sb, characters, EConstants.ADD_SUMMONS.getInt());
		appendStatLine(sb, characters, EConstants.ADD_DAMAGE.getInt());
		appendStatLine(sb, characters, EConstants.ADD_TRAP_DAMAGE.getInt());
		sb.append("0,0,0,0,0|"); // Maitrise armes, non implemente pour l'instant.
		appendStatLine(sb, characters, EConstants.ADD_DAMAGE_PERCENT.getInt());
		appendStatLine(sb, characters, EConstants.ADD_HEAL.getInt());
		appendStatLine(sb, characters, EConstants.ADD_TRAP_DAMAGE.getInt());
		appendStatLine(sb, characters, EConstants.STATS_TRAPPER.getInt());
		appendStatLine(sb, characters, EConstants.STATS_RETDOM.getInt());
		appendStatLine(sb, characters, EConstants.ADD_CRITICAL_HIT.getInt());
		appendStatLine(sb, characters, EConstants.ADD_CRITICAL_FAILURE.getInt());
		appendStatLine(sb, characters, EConstants.ADD_DODGE_AP.getInt());
		appendStatLine(sb, characters, EConstants.ADD_DODGE_MP.getInt());

		// Resistances fixes et pourcentages issues des objets equipes.
		// Ordre conserve comme dans le squelette historique Ancestra :
		// neutre fixe, neutre %, neutre PvP, neutre % PvP, puis terre/eau/air/feu.
		appendStatLine(sb, characters, EConstants.RESIST_NEUTRAL.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_NEUTRAL.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PVP_NEUTRAL.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_NEUTRAL.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_EARTH.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_EARTH.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PVP_EARTH.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_EARTH.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_WATER.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_WATER.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PVP_WATER.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_WATER.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_AIR.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_AIR.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PVP_AIR.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_AIR.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_FIRE.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_FIRE.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PVP_FIRE.getInt());
		appendStatLine(sb, characters, EConstants.RESIST_PERCENT_FIRE.getInt());
		/*
		sb.append(getStats().getEffect(Constants.ADD_AFLEE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.ADD_MFLEE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_NEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_NEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_PVP_NEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_PVP_NEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_TER.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_TER.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_PVP_TER.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_PVP_TER.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_EAU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_EAU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_PVP_EAU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_PVP_EAU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_AIR.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_AIR.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_PVP_AIR.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_PVP_AIR.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_FEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_FEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_R_PVP_FEU.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(getStats().getEffect(Constants.STATS_ADD_RP_PVP_FEU.getShort())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
		*/
		return sb.toString();
	}

	public static String getStatisticsMessage(Characters characters) {
		return statisticsMessage(
				characters, characters.getExperience().getExperience(),
				characters.getExperience().min(),
				characters.getExperience().max(),
				characters.getKamas(),
				characters.getStatsPoint(),
				characters.getSpellPoint(),
				characters.getAlignmentType(),
				characters.getAlignment().getLevel(), 
				characters.getAlignment().getLevel(), //TODO Grade
				characters.getAlignment().getExperience(),
				characters.getAlignment().getDishonor(),
				characters.isShowWings(),
				characters.getLife(), 
				characters.getLifeMax(),
				characters.getEnergy(),
				EConstants.ENERGY_MAX.getShort(),
				Statistic.totalWithEquipment(characters, EConstants.ADD_INITIATIVE.getInt()),
				Statistic.totalWithEquipment(characters, EConstants.ADD_PROSPECTION.getInt()));
	}
	
	//FIXME Ancestra make better !
	public static int getReqPtsToBoostStatsByClass(final int classID,final int statID,final int val)
	{
		switch(statID)
		{
		case 11 ://Vita
			return 1;
		case 12 ://Sage
			return 3;
		case 10 ://Force
			switch(classID) {
			case 11 :
				return 3;

			case 1 :
				if(val < 50)
					return 2;
				if(val < 150)
					return 3;
				if(val < 250)
					return 4;
				return 5;

			case 5 :
				if(val < 50)
					return 2;
				if(val < 150)
					return 3;
				if(val < 250)
					return 4;
				return 5;

			case 4 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 2 :
				if(val < 50)
					return 2;
				if(val < 150)
					return 3;
				if(val < 250)
					return 4;
				return 5;

			case 7 :
				if(val < 50)
					return 2;
				if(val < 150)
					return 3;
				if(val < 250)
					return 4;
				return 5;

			case 12 :
				if(val < 50)
					return 1;
				if(val < 200)
					return 2;
				return 3;

			case 10 :
				if(val < 50)
					return 1;
				if(val < 250)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 9 :
				if(val < 50)
					return 1;
				if(val < 150)
					return 2;
				if(val < 250)
					return 3;
				if(val < 350)
					return 4;
				return 5;

			case 3 :
				if(val < 50)
					return 1;
				if(val < 150)
					return 2;
				if(val < 250)
					return 3;
				if(val < 350)
					return 4;
				return 5;	

			case 6 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 8 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			}
			break;
		case 13 ://Chance
			switch(classID)
			{
			case 1 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 5 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 11 :
				return 3;

			case 4 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 10 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 12 :
				if(val < 50)
					return 1;
				if(val < 200)
					return 2;
				return 3;

			case 8 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 3 :
				if(val < 100)
					return 1;
				if(val < 150)
					return 2;
				if(val < 230)
					return 3;
				if(val < 330)
					return 4;
				return 5;

			case 2 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 6 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 7 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 9 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;
			}
			break;
		case 14 ://Agilit�
			switch(classID)
			{
			case 1 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 5 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 11 :
				return 3;

			case 4 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 10 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 12 :
				if(val < 50)
					return 1;
				if(val < 200)
					return 2;
				return 3;

			case 7 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 8 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 3 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;	

			case 6 :
				if(val < 50)
					return 1;
				if(val < 100)
					return 2;
				if(val < 150)
					return 3;
				if(val < 200)
					return 4;
				return 5;

			case 9 :
				if(val < 50)
					return 1;
				if(val < 100)
					return 2;
				if(val < 150)
					return 3;
				if(val < 200)
					return 4;
				return 5;

			case 2 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;
			}
			break;
		case 15 ://Intelligence
			switch(classID)
			{
			case 5 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 1 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 11 :
				return 3;

			case 4 :
				if(val < 50)
					return 2;
				if(val < 150)
					return 3;
				if(val < 250)
					return 4;
				return 5;

			case 10 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 3 :
				if(val < 20)
					return 1;
				if(val < 60)
					return 2;
				if(val < 100)
					return 3;
				if(val < 140)
					return 4;
				return 5;	

			case 12 :
				if(val < 50)
					return 1;
				if(val < 200)
					return 2;
				return 3;

			case 8 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;

			case 7 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 9 :
				if(val < 50)
					return 1;
				if(val < 150)
					return 2;
				if(val < 250)
					return 3;
				if(val < 350)
					return 4;
				return 5;

			case 2 :
				if(val < 100)
					return 1;
				if(val < 200)
					return 2;
				if(val < 300)
					return 3;
				if(val < 400)
					return 4;
				return 5;

			case 6 :
				if(val < 20)
					return 1;
				if(val < 40)
					return 2;
				if(val < 60)
					return 3;
				if(val < 80)
					return 4;
				return 5;
			}
			break;
		}
		return 5;
	}

}
