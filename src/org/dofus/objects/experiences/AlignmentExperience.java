package org.dofus.objects.experiences;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.ExperiencesData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;

public class AlignmentExperience extends AExperience {

	private short level;
	private long honor;
	private byte dishonor;
	private Experience template;
	private Characters character;
	
	public AlignmentExperience(short level, long honor, byte dishonor, Experience template, Characters character) {
		this.level = level;
		this.honor = honor;
		this.dishonor = dishonor;
		this.template = template;
		this.character = character;
	}
 
	@Override
	public void add(long exp) {
		honor += exp; 
		
		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
    	boolean up = false;
    	
    	while(honor >= ExperiencesData.get((short) (level + 1)).getAlignment() && level < 10) {
    		onLevelUp(exp);
        	up = true;
    	}
    	
    	session.write("Im080;" + exp);
    	
    	if(up == true)
    		session.write("Im082;" + level);
    	
    	session.write(Statistic.getStatisticsMessage(character));
	}

	@Override
	public void remove(long exp) {
		//TODO: Calcul deshonor (i have the formulas but need to make fight before)
		honor -= exp; 
		
		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
    	boolean down = false;
    	
    	while(honor >= ExperiencesData.get((short) (level - 1)).getAlignment() && level > 1) {
    		onLevelDown(exp);
    		down = true;
    	}
    	
    	session.write("Im081;" + exp);
    	
    	if(down == true)
    		session.write("Im083;" + level);
    	
    	session.write(Statistic.getStatisticsMessage(character));
	}

	@Override
	public void onLevelUp(long exp) {
		level++;
		template = ExperiencesData.get(level);
	}

	@Override
	public void onLevelDown(long exp) {
		level--;
		template = ExperiencesData.get(level);
	}
	
	@Override
	public long min() {
		return template.getAlignment();
	}

	@Override
	public long max() {
		return ExperiencesData.get((short) (level + 1)).getAlignment();
	}

	@Override
	public short getLevel() {
		return level;
	}

	@Override
	public long getExperience() {
		return honor;
	}
	
	@Override
	public Experience getTemplate() {
		return template;
	}

	@Override
	public Characters getCharacter() {
		return character;
	}
	
	public byte getDishonor() {
		return dishonor;
	}
	
	public void addDishonor() {
		dishonor++;
	}
	
	//TODO: Im??
	public void removeDishonor() {
		dishonor--;
	}
}
