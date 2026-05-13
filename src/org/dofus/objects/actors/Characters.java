package org.dofus.objects.actors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.dofus.constants.EConstants;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.characters.Channel;
import org.dofus.objects.characters.Emote;
import org.dofus.objects.characters.Party;
import org.dofus.objects.characters.Restriction;
import org.dofus.objects.characters.Right;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.characters.breeds.Breed;
import org.dofus.objects.characters.breeds.Spell;
import org.dofus.objects.experiences.AlignmentExperience;
import org.dofus.objects.experiences.CharacterExperience;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.spells.KnownSpell;

public class Characters implements IActor {

	private int id;
	private Account owner;
	private boolean rangeModerator;
	
	private String name;
	private Breed breed;
	private byte gender;
	private int color1, color2, color3;
	private short skin, size;

	private MapTemplate currentMap;
	private short currentCell;
	private EOrientation currentOrientation;
	private int saveMap;
	private short saveCell;

	private Right right;
	private Restriction restriction;

	private Channel channel;
	private Emote emote;

	private short life;

	private short energy;

	private CharacterExperience experience;

	private long kamas;

	public Statistic stats;
	private short statsPoint;

	private Spell spells;
	private short spellPoint;
	private final LinkedHashMap<Integer, KnownSpell> spellBook = new LinkedHashMap<>();

	// TODO: Faire une classe Alignment -> AlignmentExp pour tout réunir en une
	// classe
	private byte alignmentType;
	private short alignmentGrade;
	private AlignmentExperience alignment;
	private boolean showWings;

	private Party party;
	private String invitation;
	
	private boolean     displacement = false;
	private boolean     connected    = false;
	private NpcTemplate dialogNpc    = null;

	/** Inventaire du personnage (initialisé au chargement ou à la création). */
	private Inventory   inventory    = new Inventory();

	public Characters(int id, Account owner, String name, Breed breed, byte gender, int color1, int color2, int color3,
			short skin, short size, MapTemplate currentMap, short currentCell, EOrientation currentOrientation,
			Right right, Restriction restriction, short life, short energy, CharacterExperience experience, long kamas,
			ConcurrentMap<Integer, Integer> statistic, short statsPoint, short spellPoint, byte alignmentType,
			AlignmentExperience alignment, boolean showWings) {
		this.setId(id);
		this.setOwner(owner);
		this.setName(name);
		this.setBreed(breed);
		this.setGender(gender);
		this.setColor1(color1);
		this.setColor2(color2);
		this.setColor3(color3);
		this.setSkin(skin);
		this.setSize(size);
		this.setCurrentMap(currentMap);
		this.setCurrentCell(currentCell);
		this.setCurrentOrientation(currentOrientation);
		this.setRight(right);
		this.setRestriction(restriction);
		this.setChannel(new Channel(new ArrayList<String>())); // TODO : implement in Characters()
		this.setLife(life);
		this.setEnergy(energy);
		this.setExperience(experience);
		this.setKamas(kamas);
		this.setStats(new Statistic(statistic));
		this.setStatsPoint(statsPoint);
		this.setSpellPoint(spellPoint);
		this.setAlignmentType(alignmentType);
		this.setAlignment(alignment);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Account getOwner() {
		return owner;
	}

	public void setOwner(Account owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Breed getBreed() {
		return breed;
	}

	public byte getBreedId() {
		return getBreed().getId();
	}
	
	public void setBreed(Breed breed) {
		this.breed = breed;
	}

	public byte getGender() {
		return gender;
	}

	public void setGender(byte gender) {
		this.gender = gender;
	}

	public int getColor1() {
		return color1;
	}

	public void setColor1(int color1) {
		this.color1 = color1;
	}

	public int getColor2() {
		return color2;
	}

	public void setColor2(int color2) {
		this.color2 = color2;
	}

	public int getColor3() {
		return color3;
	}

	public void setColor3(int color3) {
		this.color3 = color3;
	}

	public short getSkin() {
		return skin;
	}

	public void setSkin(short skin) {
		this.skin = skin;
	}

	public short getSize() {
		return size;
	}

	public void setSize(short size) {
		this.size = size;
	}

	public MapTemplate getCurrentMap() {
		return currentMap;
	}

	public void setCurrentMap(MapTemplate currentMap) {
		this.currentMap = currentMap;
	}

	public short getCurrentCell() {
		return currentCell;
	}

	public void setCurrentCell(short currentCell) {
		this.currentCell = currentCell;
	}

	public EOrientation getCurrentOrientation() {
		return currentOrientation;
	}

	public void setCurrentOrientation(EOrientation currentOrientation) {
		this.currentOrientation = currentOrientation;
	}

	public int getSaveMap() {
		return saveMap > 0 ? saveMap : (currentMap != null ? currentMap.getId() : 0);
	}

	public void setSaveMap(int saveMap) {
		this.saveMap = saveMap;
	}

	public short getSaveCell() {
		return saveCell > 0 ? saveCell : currentCell;
	}

	public void setSaveCell(short saveCell) {
		this.saveCell = saveCell;
	}

	public Right getRight() {
		return right;
	}

	public void setRight(Right right) {
		this.right = right;
	}

	public Restriction getRestriction() {
		return restriction;
	}

	public void setRestriction(Restriction restriction) {
		this.restriction = restriction;
	}

	public Channel getChannel() {
		return channel;
	}

	public String getChannels() {
		return channel.get();
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public Emote getEmote() {
		return emote;
	}

	public void setEmote(Emote emote) {
		this.emote = emote;
	}

	public short getLife() {
		return life;
	}

	public void setLife(short life) {
		this.life = life;
	}

	public short getLifeMax() {
		int baseLife = Math.max(55, getBreed().getLife());
		return (short) (baseLife + 5 * (getExperience().getLevel() - 1)
				+ Statistic.totalWithEquipment(this, EConstants.ADD_VITALITY.getInt()));
	}

	public short getEnergy() {
		return energy;
	}

	public void setEnergy(short energy) {
		this.energy = energy;
	}

	public CharacterExperience getExperience() {
		return experience;
	}

	public void setExperience(CharacterExperience experience) {
		this.experience = experience;
	}

	public long getKamas() {
		return kamas;
	}

	public void setKamas(long kamas) {
		this.kamas = kamas;
	}

	public Statistic getStats() {
		return stats;
	}

	public void setStats(Statistic stats) {
		this.stats = stats;
		if(this.stats != null) this.stats.ensureDefaults(this);
	}

	public short getStatsPoint() {
		return statsPoint;
	}

	public void setStatsPoint(short statsPoint) {
		this.statsPoint = statsPoint;
	}

	public Spell getSpells() {
		return spells;
	}

	public void setSpells(Spell spells) {
		this.spells = spells;
	}

	public Map<Integer, KnownSpell> getSpellBook() {
		return spellBook;
	}

	public KnownSpell getKnownSpell(int spellId) {
		return spellBook.get(spellId);
	}

	public void learnSpell(KnownSpell spell) {
		if(spell != null) spellBook.put(spell.getSpellId(), spell);
	}

	public short getSpellPoint() {
		return spellPoint;
	}

	public void setSpellPoint(short spellPoint) {
		this.spellPoint = spellPoint;
	}

	public byte getAlignmentType() {
		return alignmentType;
	}

	public void setAlignmentType(byte alignmentType) {
		this.alignmentType = alignmentType;
	}

	public short getAlignmentGrade() {
		return alignmentGrade;
	}

	public void setAlignmentGrade(short alignmentGrade) {
		this.alignmentGrade = alignmentGrade;
	}

	public AlignmentExperience getAlignment() {
		return alignment;
	}

	public void setAlignment(AlignmentExperience alignment) {
		this.alignment = alignment;
	}

	public boolean isShowWings() {
		return showWings;
	}

	public void setShowWings(boolean showWings) {
		this.showWings = showWings;
	}

	public Party getParty() {
		return party;
	}

	public void setParty(Party party) {
		this.party = party;
	}

	public String getInvitation() {
		return invitation;
	}

	public void setInvitation(String invitation) {
		this.invitation = invitation;
	}

	public boolean isDisplacement() {
		return displacement;
	}

	public void setDisplacement(boolean displacement) {
		this.displacement = displacement;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public NpcTemplate getDialogNpc() {
		return dialogNpc;
	}

	public void setDialogNpc(NpcTemplate dialogNpc) {
		this.dialogNpc = dialogNpc;
	}

	public Inventory getInventory() {
		return inventory;
	}

	public void setInventory(Inventory inventory) {
		this.inventory = inventory;
	}

	public short getMaxPods() {
		int strength = Statistic.totalWithEquipment(this, EConstants.ADD_STRENGTH.getInt());
		int bonusPods = Statistic.equipmentBonus(this, EConstants.ADD_WEIGHT.getInt());
		return (short) (1000 + strength * 5 + bonusPods);
	}

	public String parseParty()
	{
		StringBuilder str = new StringBuilder(20);
		str.append(id).append(';');
		str.append(name).append(';');
		str.append(skin).append(';');
		str.append(color1).append(';');
		str.append(color2).append(';');
		str.append(color3).append(';');
		str.append("").append(';');//TODO Gm stuff
		str.append(life).append(',').append(getLifeMax()).append(';');
		str.append(experience.getLevel()).append(';');
		str.append(0).append(';');//Initiative
		str.append(0).append(';');//Total stats prospec
		str.append('1');//? TODO
		return str.toString();
	}
	
	@Override
	public int getActorId() {
		return getId();
	}

	@Override
	public int getActorType() {
		return GameActorTypeEnum.TYPE_CHARACTER.getActorType();
	}

	@Override
	public EOrientation getOrientation() {
		return getCurrentOrientation();
	}

	@Override
	public MapTemplate getMapId() {
		return getCurrentMap();
	}

	@Override
	public int getCellId() {
		return getCurrentCell();
	}
}
