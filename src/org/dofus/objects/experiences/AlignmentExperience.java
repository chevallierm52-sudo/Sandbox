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
		if(session == null || !session.isConnected()) return;

		boolean up = false;
		while(level < 10) {
			Experience next = ExperiencesData.get((short) (level + 1));
			if(next == null || honor < next.getAlignment()) break;
			onLevelUp(exp);
			up = true;
		}

		session.write("Im080;" + exp);
		if(up) session.write("Im082;" + level);
		session.write(Statistic.getStatisticsMessage(character));
	}

	@Override
	public void remove(long exp) {
		honor -= exp;
		if(honor < 0) honor = 0;

		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
		if(session == null || !session.isConnected()) return;

		boolean down = false;
		while(level > 1 && honor < template.getAlignment()) {
			onLevelDown(exp);
			down = true;
		}

		session.write("Im081;" + exp);
		if(down) session.write("Im083;" + level);
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

	@Override public long min()          { return template.getAlignment(); }
	@Override public long max()          { Experience next = ExperiencesData.get((short)(level+1)); return next != null ? next.getAlignment() : Long.MAX_VALUE; }
	@Override public short getLevel()    { return level; }
	@Override public long getExperience(){ return honor; }
	@Override public Experience getTemplate() { return template; }
	@Override public Characters getCharacter() { return character; }

	public byte getDishonor()   { return dishonor; }
	public void addDishonor()   { dishonor++; }
	public void removeDishonor(){ dishonor--; }
}
