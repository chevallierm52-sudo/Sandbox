package org.dofus.objects.characters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.constants.EConstants;
import org.dofus.objects.actors.Characters;

public class Statistic {

	private final ConcurrentMap<Integer, Integer> statistics;

	//Loading character
	public Statistic(ConcurrentMap<Integer, Integer> stats) {
		this.statistics = stats;
	}
	
	//Create
	public Statistic(Characters character) {
		this.statistics = new ConcurrentHashMap<Integer, Integer>();
		statistics.put(EConstants.ADD_STRENGTH.getInt(), 0);
		statistics.put(EConstants.ADD_WISDOM.getInt(), 0);
		statistics.put(EConstants.ADD_INTELLIGENCE.getInt(), 0);
		statistics.put(EConstants.ADD_CHANCE.getInt(), 0);
		statistics.put(EConstants.ADD_AGILITY.getInt(), 0);
		 
		statistics.put(EConstants.ADD_AP.getInt(), character.getBreed().getAp());
		statistics.put(EConstants.ADD_MP.getInt(), character.getBreed().getMp());
		statistics.put(EConstants.ADD_PROSPECTION.getInt(), character.getBreed().getProspecting());
		statistics.put(EConstants.ADD_WEIGHT.getInt(), 1000);
		statistics.put(EConstants.ADD_SUMMONS.getInt(), 1);
		statistics.put(EConstants.ADD_INITIATIVE.getInt(), 1);
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
		if(statistics.get(id) == null || statistics.get(id) == 0)
			return statistics.put(id, value);
		else
			return statistics.put(id, statistics.get(id) + value);
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
	
		// TODO make cache
		sb.append(characters.getStats().getEffect(EConstants.ADD_AP.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_MP.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_STRENGTH.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_VITALITY.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_WISDOM.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_CHANCE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_AGILITY.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_RANGE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_SUMMONS.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_DAMAGE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_TRAP_DAMAGE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append("0,0,0,0|"); // FIXME Maitrise ?
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_DAMAGE_PERCENT.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_HEAL.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_TRAP_DAMAGE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.STATS_TRAPPER.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.STATS_RETDOM.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_CRITICAL_HIT.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
	
		sb.append(characters.getStats().getEffect(EConstants.ADD_CRITICAL_FAILURE.getInt())).append(',') // get base
				.append(0).append(',') // get equip
				.append(0).append(',') // get gift
				.append(0).append(',') // get buff
				.append(0).append('|'); // get safe total
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
				characters.getStats().getEffect(EConstants.ADD_INITIATIVE.getInt()), //TODO Formulas
				characters.getStats().getEffect(EConstants.ADD_PROSPECTION.getInt()));
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
