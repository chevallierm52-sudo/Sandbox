package org.dofus.objects.actors;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.BreedsData;
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

public class BotAI {

	public static Characters botAI(String name, int mapId, short cellId) throws Exception {
		Account account = new Account(-1, "bot_" + name, "", "", "", "", false);
		Characters bot = new Characters(
				-1000,
				account,
				name,
				BreedsData.get((byte) 1), 
				(byte) 0, 
				-1, 
				-1, 
				-1, 
				(short) (((byte) 1 * 10) + 0), //Skin
				EConstants.DEFAULT_SIZE.getShort(),
				MapsData.findById(7411), //map id 
				(short) 250,
				EOrientation.SOUTH_EAST,
				new Right(8192),
				new Restriction(0),
				BreedsData.get((byte) 1).getLife(),
				(short) 10000, //Energy
				null, //Experience null because we call it after
				0, //Kamas 
				new ConcurrentHashMap<Integer, Integer>(), 
				(short) 0, //Stats point 
				(short) 0, //Spells point
				(byte) 0, //Default alignment
				null, //Alignment 
				false //Show wings
		);
		
		bot.setExperience(new CharacterExperience(
				(short) 1, //Start level
				ExperiencesData.get((short) 1).getCharacter(),
				ExperiencesData.get((short) 1),
				bot));
		
		bot.setAlignment(new AlignmentExperience(
				(short) 0, //Start align
				(long) 0, //Start honor
				(byte) 0, //Start dishonor
				ExperiencesData.get(EConstants.DEFAULT_LEVEL.getShort()),
				bot));
		
		bot.setStats(new Statistic(bot));
		
		bot.getCurrentMap().addActor(bot);
		WorldData.addCharacterById(bot, bot.getId());
		WorldData.addCharacterByName(bot, bot.getName());
		
		broadcast(bot);
		System.out.println("Bot spawn");
		return bot;
	}

	private static void broadcast(Characters bot) {
		StringBuilder packet = new StringBuilder("GM|+");
		GProtocol.getCharacterPattern(packet, bot);
		for(Characters actor : bot.getCurrentMap().getActors().values()) {
			if(actor == bot) continue;
			IoSession session = WorldData.getSessionByAccount().get(actor.getOwner());
			if(session != null && session.isConnected())
				session.write(packet.toString());
		}
	}
	
		
}