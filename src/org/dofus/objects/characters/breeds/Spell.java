package org.dofus.objects.characters.breeds;

public class Spell {

	private int breedId;
	private int spellId;
	private int level;
	private int position;
	
	public Spell(int breedId, int spellId, int level, int position) {
		this.setBreedId(breedId);
		this.setSpellId(spellId);
		this.setLevel(level);
		this.setPosition(position);
	}

	public int getBreedId() {
		return breedId;
	}
 
	public void setBreedId(int breedId) {
		this.breedId = breedId;
	}

	public int getSpellId() {
		return spellId;
	}

	public void setSpellId(int spellId) {
		this.spellId = spellId;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}
}
