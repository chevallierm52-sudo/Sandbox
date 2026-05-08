package org.dofus.objects.characters;

public class Right {

	private boolean CAN_ASSAULT;
	private boolean CAN_CHALLENGE;
	private boolean CAN_EXCHANGE;
	private boolean CAN_ATTACK;
	private boolean CAN_CHAT_TO_ALL;
	private boolean CAN_BE_MERCHANT;
	private boolean CAN_USE_OBJECT;
	private boolean CANT_INTERACT_WITH_TAX_COLLECTOR;
	private boolean CAN_USE_INTERACTIVE_OBJECTS;
	private boolean CANT_SPEAK_NPC;
	private boolean CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT;
	private boolean CAN_MOVE_ALL_DIRECTIONS;
	private boolean CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT;
	private boolean CANT_INTERACT_WITH_PRISM;
	 
	public Right() {

	}

	public Right(int rights) {
		CAN_ASSAULT = (rights & 1) != 1;
		CAN_CHALLENGE = (rights & 2) != 2;
		CAN_EXCHANGE = (rights & 4) != 4;
		CAN_ATTACK = (rights & 8) == 8;
		CAN_CHAT_TO_ALL = (rights & 16) != 16;
		CAN_BE_MERCHANT = (rights & 32) != 32;
		CAN_USE_OBJECT = (rights & 64) != 64;
		CANT_INTERACT_WITH_TAX_COLLECTOR = (rights & 128) != 128;
		CAN_USE_INTERACTIVE_OBJECTS = (rights & 256) != 256;
		CANT_SPEAK_NPC = (rights & 512) != 512;
		CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT = (rights & 4096) == 4096;
		CAN_MOVE_ALL_DIRECTIONS = (rights & 8192) == 8192;
		CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT = (rights & 16384) == 16384;
		CANT_INTERACT_WITH_PRISM = (rights & 32768) != 32768;
	}

	public int get() {
		int rights = 0;

		if(!CAN_ASSAULT)
			rights++;
		if(!CAN_CHALLENGE)
			rights += 2;
		if(!CAN_EXCHANGE)
			rights += 4;
		if(CAN_ATTACK)
			rights += 8;
		if(!CAN_CHAT_TO_ALL)
			rights += 16;
		if(!CAN_BE_MERCHANT)
			rights += 32;
		if(!CAN_USE_OBJECT)
			rights += 64;
		if(!CANT_INTERACT_WITH_TAX_COLLECTOR)
			rights += 128;
		if(!CAN_USE_INTERACTIVE_OBJECTS)
			rights += 256;
		if(CANT_SPEAK_NPC)
			rights += 512;
		if(CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT)
			rights += 4096;
		if(CAN_MOVE_ALL_DIRECTIONS)
			rights += 8192;
		if(CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT)
			rights += 16384;
		if(!CANT_INTERACT_WITH_PRISM)
			rights += 32768;

		return rights;
	}

	public String toBase36() {
		String toReturn = "";
		try {
			toReturn = Integer.toString(get(), 36);
		} catch(Throwable ex) {
			toReturn = "6bk"; // default value
		}
		return toReturn;
	}

	public void addAll() {
		CAN_ASSAULT = true;
		CAN_CHALLENGE = true;
		CAN_EXCHANGE = true;
		CAN_ATTACK = true;
		CAN_CHAT_TO_ALL = true;
		CAN_BE_MERCHANT = true;
		CAN_USE_OBJECT = true;
		CANT_INTERACT_WITH_TAX_COLLECTOR = true;
		CAN_USE_INTERACTIVE_OBJECTS = true;
		CANT_SPEAK_NPC = true;
		CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT = true;
		CAN_MOVE_ALL_DIRECTIONS = true;
		CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT = true;
		CANT_INTERACT_WITH_PRISM = true;
	}

	public void setDefault() {
		CAN_ASSAULT = false;
		CAN_CHALLENGE = false;
		CAN_EXCHANGE = false;
		CAN_ATTACK = false;
		CAN_CHAT_TO_ALL = false;
		CAN_BE_MERCHANT = false;
		CAN_USE_OBJECT = false;
		CANT_INTERACT_WITH_TAX_COLLECTOR = false;
		CAN_USE_INTERACTIVE_OBJECTS = false;
		CANT_SPEAK_NPC = false;
		CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT = false;
		CAN_MOVE_ALL_DIRECTIONS = true;
		CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT = false;
		CANT_INTERACT_WITH_PRISM = false;
	}
	
	public void setAssault(boolean value) {
		CAN_ASSAULT = value;
	}

	public boolean canAssault() {
		return CAN_ASSAULT;
	}

	public void setCanChallenge(boolean value) {
		CAN_CHALLENGE = value;
	}

	public boolean canChallenge() {
		return CAN_CHALLENGE;
	}

	public void setCanExchange(boolean value) {
		CAN_EXCHANGE = value;
	}

	public boolean canExchange() {
		return CAN_EXCHANGE;
	}

	public void setCanAttack(boolean value) {
		CAN_ATTACK = value;
	}

	public boolean canAttack() {
		return CAN_ATTACK;
	}

	public boolean canChatWithAll() {
		return CAN_CHAT_TO_ALL;
	}

	public void setCanChatWithAll(boolean value) {
		CAN_CHAT_TO_ALL = value;
	}

	public boolean canBeMerchant() {
		return CAN_BE_MERCHANT;
	}

	public void setCanBeMerchant(boolean value) {
		CAN_BE_MERCHANT = value;
	}

	public boolean canUseObject() {
		return CAN_USE_OBJECT;
	}

	public void setCanUseObject(boolean value) {
		CAN_USE_OBJECT = value;
	}

	public boolean cantInteractWithTaxCollector() {
		return CANT_INTERACT_WITH_TAX_COLLECTOR;
	}

	public void setCantInteractWithTaxCollector(boolean value) {
		CANT_INTERACT_WITH_TAX_COLLECTOR = value;
	}

	public boolean canUseInteractiveObject() {
		return CAN_USE_INTERACTIVE_OBJECTS;
	}

	public void setCanUseInteractiveObject(boolean value) {
		CAN_USE_INTERACTIVE_OBJECTS = value;
	}

	public boolean cantSpeakWithNPC() {
		return CANT_SPEAK_NPC;
	}

	public void setCantSpeakWithNPC(boolean value) {
		CANT_SPEAK_NPC = value;
	}

	public boolean canAttackDungeonMonstersWhenMutant() {
		return CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT;
	}

	public void setCanAttackDungeonMobsWhenMutant(boolean value) {
		CAN_ATTACK_DUNGEON_MONSTERS_WHEN_MUTANT = value;
	}

	public boolean canMoveAllDirections() {
		return CAN_MOVE_ALL_DIRECTIONS;
	}

	public void setCanMoveAllDirections(boolean value) {
		CAN_MOVE_ALL_DIRECTIONS = value;
	}

	public boolean canAttackMonstersAnyWhereWhenMutant() {
		return CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT;
	}

	public void setCanAttackMonstersAnyWhereWhenMutant(boolean value) {
		CAN_ATTACK_MONSTERS_ANYWHERE_WHEN_MUTANT = value;
	}

	public boolean cantInteractWithPrism() {
		return CANT_INTERACT_WITH_PRISM;
	}

	public void setCantInteractWithPrism(boolean value) {
		CANT_INTERACT_WITH_PRISM = value;
	}
}
