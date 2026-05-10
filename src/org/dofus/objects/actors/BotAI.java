package org.dofus.objects.actors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.dofus.constants.EConstants;
import org.dofus.database.objects.BreedsData;
import org.dofus.network.game.BotClient;
import org.dofus.database.objects.ExperiencesData;
import org.dofus.database.objects.MapsData;
import org.dofus.network.game.protocols.GProtocol;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.characters.Restriction;
import org.dofus.objects.characters.Right;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.experiences.AlignmentExperience;
import org.dofus.objects.experiences.CharacterExperience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotAI {

	private static final Logger logger = LoggerFactory.getLogger(BotAI.class);

	// IDs négatifs uniques pour ne pas entrer en conflit avec les vrais personnages
	private static final AtomicInteger BOT_ID = new AtomicInteger(-1000);

	/** Personnalité assignée à chaque bot (botId → personnalité). */
	private static final Map<Integer, BotPersonality> personalities = new ConcurrentHashMap<>();

	/**
	 * Configuration des bots.
	 * [name, mapId, cellId, breedId, gender, color1, color2, color3, level, BotPersonality]
	 */
	private static final Object[][] BOT_CONFIGS = {
		{ "Alyndra",  7411, (short) 270, (byte) 3,  (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50,  BotPersonality.SOCIAL    }, // Eniripsa F
		{ "Torvan",   7411, (short) 300, (byte) 8,  (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78,  BotPersonality.EXPLORER  }, // Cra M
		{ "Myralis",  7411, (short) 320, (byte) 2,  (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34,  BotPersonality.SOCIAL    }, // Osamodas F
		{ "Bruthax",  7411, (short) 250, (byte) 9,  (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95,  BotPersonality.WARRIOR   }, // Sacrieur M
		{ "Selenne",  7411, (short) 340, (byte) 6,  (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21,  BotPersonality.EXPLORER  }, // Ecaflip F
		{ "Drakthar", 7411, (short) 360, (byte) 4,  (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT  }, // Sram M
		{ "Lyria",    7411, (short) 380, (byte) 5,  (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67,  BotPersonality.SOCIAL    }, // Xelor F
		{ "Gondar",   7411, (short) 210, (byte) 7,  (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43,  BotPersonality.WARRIOR   }, // Iop M
		{ "Vaelith",  7411, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88,  BotPersonality.EXPLORER  }, // Pandawa F
		{ "Orlath",   7411, (short) 400, (byte) 1,  (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29,  BotPersonality.MERCHANT  }, // Feca M
	};

	/** Retourne la personnalité d'un bot, ou null si inconnu. */
	public static BotPersonality getPersonality(int botId) {
		return personalities.get(botId);
	}

	/** Crée et démarre tous les bots configurés. */
	public static void spawnAll() throws Exception {
		// botId → bot, pour construire les groupes d'amis après spawn
		java.util.Map<Integer, Characters> spawned = new java.util.LinkedHashMap<>();

		for(Object[] cfg : BOT_CONFIGS) {
			String      name   = (String)      cfg[0];
			int         mapId  = (int)         cfg[1];
			short       cellId = (short)       cfg[2];
			byte        breed  = (byte)        cfg[3];
			byte        gender = (byte)        cfg[4];
			int         c1     = (int)         cfg[5];
			int         c2     = (int)         cfg[6];
			int         c3     = (int)         cfg[7];
			short       level  = (short)       cfg[8];
			BotPersonality pers = (BotPersonality) cfg[9];

			try {
				Characters bot = create(name, mapId, cellId, breed, gender, c1, c2, c3, level);
				personalities.put(bot.getId(), pers);
				spawned.put(bot.getId(), bot);
				BotBehavior.start(bot);
			} catch(Exception e) {
				logger.error("Failed to spawn bot {}: {}", name, e.getMessage());
			}
		}

		// ── Groupes d'amis par personnalité ─────────────────────────────────
		// Les bots de même personnalité se connaissent et peuvent se suivre.
		java.util.Map<BotPersonality, java.util.List<Integer>> byPersonality = new java.util.EnumMap<>(BotPersonality.class);
		for(java.util.Map.Entry<Integer, Characters> entry : spawned.entrySet()) {
			BotPersonality p = personalities.get(entry.getKey());
			if(p != null)
				byPersonality.computeIfAbsent(p, k -> new java.util.ArrayList<>()).add(entry.getKey());
		}
		for(java.util.List<Integer> group : byPersonality.values()) {
			int[] ids = group.stream().mapToInt(Integer::intValue).toArray();
			if(ids.length >= 2) BotSocial.addFriendGroup(ids);
		}
		// Liens inter-personnalités : SOCIAL ↔ EXPLORER (les curieux aiment discuter)
		java.util.List<Integer> socials   = byPersonality.getOrDefault(BotPersonality.SOCIAL,   java.util.Collections.emptyList());
		java.util.List<Integer> explorers = byPersonality.getOrDefault(BotPersonality.EXPLORER, java.util.Collections.emptyList());
		for(int s : socials)
			for(int e : explorers)
				BotSocial.addFriendship(s, e);

		logger.info("{} bots spawned, groupes d'amis enregistrés", spawned.size());
	}

	private static Characters create(String name, int mapId, short cellId,
			byte breedId, byte gender, int color1, int color2, int color3, short level) throws Exception {

		int botId = BOT_ID.getAndDecrement();

		Account account = new Account(botId, "bot_" + name, "", "", "", name, false);

		Characters bot = new Characters(
				botId,
				account,
				name,
				BreedsData.get(breedId),
				gender,
				color1, color2, color3,
				(short) (breedId * 10 + gender),
				EConstants.DEFAULT_SIZE.getShort(),
				MapsData.findById(mapId),
				cellId,
				EOrientation.SOUTH_EAST,
				new Right(8192),
				new Restriction(0),
				(short)(BreedsData.get(breedId).getLife() + 5 * (level - 1)),
				(short) 10000,
				null,
				0,
				new ConcurrentHashMap<>(),
				(short) 0,
				(short) 0,
				(byte) 0,
				null,
				false
		);

		bot.setExperience(new CharacterExperience(
				level,
				ExperiencesData.get(level).getCharacter(),
				ExperiencesData.get(level),
				bot));

		bot.setAlignment(new AlignmentExperience(
				(short) 0, 0L, (byte) 0,
				ExperiencesData.get(EConstants.DEFAULT_LEVEL.getShort()),
				bot));

		bot.setStats(new Statistic(bot));
		bot.setConnected(true);

		bot.getCurrentMap().addActor(bot);
		WorldData.addCharacterById(bot, bot.getId());
		WorldData.addCharacterByName(bot, bot.getName());

		// Enregistrer comme client complet → bot visible et interactif
		BotClient botClient = new BotClient(bot);
		botClient.register();

		broadcast(bot);
		logger.info("Bot {} (id={} breed={} lvl={}) spawned on map {}",
			new Object[]{ name, botId, breedId, level, mapId });
		return bot;
	}

	private static void broadcast(Characters bot) {
		StringBuilder packet = new StringBuilder("GM|+");
		GProtocol.getCharacterPattern(packet, bot);
		String packetStr = packet.toString();
		for(Characters actor : bot.getCurrentMap().getActors().values()) {
			if(actor == bot) continue;
			org.apache.mina.core.session.IoSession session =
				WorldData.getSessionByAccount().get(actor.getOwner());
			if(session != null && session.isConnected())
				session.write(packetStr);
		}
	}
}
