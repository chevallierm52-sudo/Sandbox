package org.dofus.objects.experiences;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.SpellsData;
import org.dofus.database.objects.ExperiencesData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;

public class CharacterExperience extends AExperience {

	private short level;
	private long experience;
	private Experience template;
	private Characters character;

	public CharacterExperience(short level, long experience, Experience template, Characters character) {
		this.level = level;
		this.experience = experience;
		this.template = template;
		this.character = character;
	}

	@Override
	public void add(long exp) {
		experience += exp;

		IoSession session = WorldData.getSessionByAccount().get(character.getOwner());
		if(session == null || !session.isConnected()) return;

		boolean up = false;
		while(level < 200) {
			Experience next = ExperiencesData.get((short) (level + 1));
			if(next == null || experience < next.getCharacter()) break;
			onLevelUp(exp);
			up = true;
		}

		if(up) {
			SpellsData.loadCharacterSpells(character);
			session.write("AN" + level);
			session.write(SpellsData.buildSLPacket(character.getSpellBook()));
		}
		session.write(Statistic.getStatisticsMessage(character));
	}

	@Override
	public void remove(long exp) {}

	@Override
	public void onLevelUp(long exp) {
		level++;
		template = ExperiencesData.get(level);
		character.setLife(character.getLifeMax());
		character.setStatsPoint((short) (character.getStatsPoint() + 5));
		character.setSpellPoint((short) (character.getSpellPoint() + 1));

		if(level == 100)
			character.getStats().add(EConstants.ADD_AP.getInt(), 1);
	}

	@Override
	public void onLevelDown(long exp) {}


	/**
	 * Fixe directement le niveau du personnage pour les commandes GM/debug.
	 * L'XP est recalée sur le seuil minimum officiel du niveau.
	 */
	public void setLevel(short newLevel) {
		if(newLevel < 1) newLevel = 1;
		if(newLevel > 200) newLevel = 200;
		this.level = newLevel;
		this.template = ExperiencesData.get(newLevel);
		this.experience = this.template != null ? this.template.getCharacter() : 0;
	}

	@Override public long min()           { return template.getCharacter(); }
	@Override public long max()           { Experience next = ExperiencesData.get((short)(level+1)); return next != null ? next.getCharacter() : Long.MAX_VALUE; }
	@Override public short getLevel()     { return level; }
	@Override public long getExperience() { return experience; }
	@Override public Experience getTemplate()   { return template; }
	@Override public Characters getCharacter()  { return character; }
}
