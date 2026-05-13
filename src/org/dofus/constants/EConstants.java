package org.dofus.constants;

public enum EConstants {

    // =========================
    // DEFAULT / CONFIG
    // =========================
    DEFAULT_LEVEL(1),
    DEFAULT_SIZE(100),

    MAX_MESSAGE_LENGTH(200),
    SMILEY_DELAY(3000),

    DOFUS_VERSION("1.29.1"),
    MAX_PLAYER_ON_SERVER(500),

    NOVICE_LEVEL(5),
    ENERGY_MAX(10000),

    SPELL_MAX_LEVEL(6),
    SPELL_LEVEL_REQUIRED(100),

    MAX_PLAYERS_IN_TEAM(8),
    MAX_PLAYERS_IN_CHALLENGE(16),
    MEMBERS_COUNT_IN_PARTY(8),

    // =========================
    // ALIGNEMENT
    // =========================
    ALIGNEMENT_NEUTRAL(-1),
    ALIGNEMENT_ANGEL(1),
    ALIGNEMENT_DEMON(2),
    ALIGNEMENT_MERCENARY(3),

    // =========================
    // ELEMENTS
    // =========================
    ELEMENT_NEUTRAL(0),
    ELEMENT_EARTH(1),
    ELEMENT_WATER(2),
    ELEMENT_FIRE(3),
    ELEMENT_AIR(4),

    // =========================
    // STATS PRINCIPALES
    // =========================
    ADD_VITALITY(125),
    ADD_WISDOM(124),
    ADD_STRENGTH(118),
    ADD_INTELLIGENCE(126),
    ADD_CHANCE(123),
    ADD_AGILITY(119),

    // =========================
    // PA / PM
    // =========================
    ADD_AP(111),
    REMOVE_AP(168),

    ADD_MP(128),
    REMOVE_MP(169),

    ADD_DODGE_AP(160),
    ADD_DODGE_MP(161),
    REMOVE_DODGE_AP(162),
    REMOVE_DODGE_MP(163),

    // =========================
    // COMBAT
    // =========================
    ADD_DAMAGE(112),
    ADD_DAMAGE_PERCENT(138),
    
    ADD_TRAP_DAMAGE(225),
    STATS_TRAPPER(226),
    
    ADD_HEAL(178),

    ADD_CRITICAL_HIT(115),
    REMOVE_CRITICAL_HIT(171),
    ADD_CRITICAL_DAMAGE(164),

    ADD_RANGE(117),
    REDUCE_RANGE(116),

    ADD_INITIATIVE(174),
    REMOVE_INITIATIVE(175),

    ADD_PROSPECTION(176),
    REMOVE_PROSPECTION(177),

    ADD_SUMMONS(182),

    // =========================
    // RETRAITS STATS
    // =========================
    REMOVE_VITALITY(153),
    REMOVE_WISDOM(156),
    REMOVE_STRENGTH(157),
    REMOVE_INTELLIGENCE(155),
    REMOVE_CHANCE(152),
    REMOVE_AGILITY(154),

    // =========================
    // RESISTANCES FIXES
    // =========================
    RESIST_NEUTRAL(183),
    RESIST_EARTH(184),
    RESIST_WATER(185),
    RESIST_FIRE(186),
    RESIST_AIR(187),

    // =========================
    // RESISTANCES %
    // =========================
    RESIST_PERCENT_NEUTRAL(210),
    RESIST_PERCENT_EARTH(211),
    RESIST_PERCENT_WATER(212),
    RESIST_PERCENT_FIRE(213),
    RESIST_PERCENT_AIR(214),

    // =========================
    // PVP RESIST
    // =========================
    RESIST_PVP_NEUTRAL(250),
    RESIST_PVP_EARTH(251),
    RESIST_PVP_WATER(252),
    RESIST_PVP_FIRE(253),
    RESIST_PVP_AIR(254),

    // =========================
    // AUTRES
    // =========================
    ADD_WEIGHT(158),
    REMOVE_WEIGHT(159),

    ADD_LIFE(110),

    MULTIPLY_DAMAGE(114),

    ADD_PHYSICAL_DAMAGE(142),
    REMOVE_PHYSICAL_DAMAGE(145),

	ADD_CRITICAL_FAILURE(122),
	
	STATS_RETDOM(220),

    // =========================
    // CORE FIX
    // =========================
    ;

    private final Integer intValue;
    private final String stringValue;

    EConstants(int value) {
        this.intValue = value;
        this.stringValue = null;
    }

    EConstants(String value) {
        this.intValue = null;
        this.stringValue = value;
    }

    public int getInt() {
        return intValue != null ? intValue : 0;
    }

    public short getShort() {
        return intValue != null ? intValue.shortValue() : 0;
    }

    public String getString() {
        return stringValue;
    }

    // =========================
    // ZAAPS
    // =========================

	public static short[][] AMAKNA_ZAAPS ={{935,295},{528,156},{9454,268},{951,126},{1242,323},{164,193},{1158,340},{8037,249},
			{8437,310},{8088,223},{8125,358},{8163,207},{10643,269},{11170,326},{1841,150},{844,212},{11210,401},{4263,170},{3022,186},
			{6855,253},{6137,104},{3250,165},{4739,354},{5295,561},{8785,253},{7411,311}, {6954,238},{2191,200}
	};
	
	public static short[][] INCARNAM_ZAAPS	= {{10297,199},{10349,282},{10304,138},{10317,195},{10114,282}
	};

	public static short[][] BONTA_ZAAPI = {{4271,420},{4174,348},{8758,657},{4299,599},{4180,672},{8759,527},{4183,398},{2221,247},
			{4308,457},{4217,473},{4098,528},{8757,540},{4223,279},{8760,360},{2214,548},{4179,297},{4229,217},{4232,506},{8478,413},{4238,354},
			{4263,134},{4216,668},{6159,253},{4172,448},{4247,251},{4272,641},{4250,168},{4178,267},{4106,304},{4181,723},{4259,136},{4090,694},
			{4262,346},{4287,131},{4300,455},{4240,449},{4218,230},{4074,142}
	};
	
	public static short[][] BRAKMAR_ZAAPI = {
		{6167,183},{4930,214},{4620,639},{4604,483},{4639,489},{4627,208},{4579,594},{8756,406},{5277,506},
		{5304,551},{5334,484},{4612,641},{4549,549},{4607,467},{8753,345},{4622,644},{4565,134},{5112,754},{4562,173},
		{8754,484},{5317,310},{4615,582},{5334,486},{4618,344},{4588,559},{8493,342},{4646,297},
		{5332,191},{8755,513},{5116,435},{4601,507},{4637,728},{4623,443},{4551,254},{5295,468}
	};

}
