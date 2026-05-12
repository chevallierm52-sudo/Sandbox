package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.characters.breeds.Breed.BreedType;
import org.dofus.utils.DeferredSaveService;
import org.dofus.utils.PacketValidator;
import org.dofus.utils.RegenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoostParser {

	private static final Logger logger = LoggerFactory.getLogger(BoostParser.class);

	/**
	 * Stat IDs sent by the client:
	 *   10 = Strength, 11 = Vitality, 12 = Wisdom
	 *   13 = Chance,   14 = Agility,  15 = Intelligence
	 *
	 * Cost formula (paliers) is in Statistic.getReqPtsToBoostStatsByClass().
	 * Sacrieur special: 1 stat point → 2 Vitality.
	 */
	public static void boost(Characters character, IoSession session, int statId) {
		// Validation anti-cheat : statId connu + valeur raisonnable
		if(!PacketValidator.validateStatBoost(session.getId(), statId, character.getStatsPoint() > 0 ? 1 : 0)) {
			session.write("BN");
			return;
		}
		int currentValue;
		switch(statId) {
			case 10: currentValue = character.getStats().getEffect(EConstants.ADD_STRENGTH.getInt());     break;
			case 11: currentValue = character.getStats().getEffect(EConstants.ADD_VITALITY.getInt());     break;
			case 12: currentValue = character.getStats().getEffect(EConstants.ADD_WISDOM.getInt());       break;
			case 13: currentValue = character.getStats().getEffect(EConstants.ADD_CHANCE.getInt());       break;
			case 14: currentValue = character.getStats().getEffect(EConstants.ADD_AGILITY.getInt());      break;
			case 15: currentValue = character.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt()); break;
			default:
				logger.warn("Unknown stat id {} for boost (character {})", statId, character.getName());
				return;
		}

		int cost = Statistic.getReqPtsToBoostStatsByClass(character.getBreed().getId(), statId, currentValue);

		if(cost > character.getStatsPoint()) {
			logger.debug("Boost refused for {} stat={} cost={} available={}",
				new Object[] { character.getName(), statId, cost, character.getStatsPoint()});
			return;
		}

		switch(statId) {
			case 10: character.getStats().add(EConstants.ADD_STRENGTH.getInt(),     1); break;
			case 11:
				int gain = (character.getBreed().getId() == BreedType.SACRIEUR.getValue()) ? 2 : 1;
				character.getStats().add(EConstants.ADD_VITALITY.getInt(), gain);
				break;
			case 12: character.getStats().add(EConstants.ADD_WISDOM.getInt(),       1); break;
			case 13: character.getStats().add(EConstants.ADD_CHANCE.getInt(),       1); break;
			case 14: character.getStats().add(EConstants.ADD_AGILITY.getInt(),      1); break;
			case 15: character.getStats().add(EConstants.ADD_INTELLIGENCE.getInt(), 1); break;
		}

		character.setStatsPoint((short) (character.getStatsPoint() - cost));
		RegenService.refresh(character, session);
		DeferredSaveService.schedule(character);

		logger.debug("Boost {} stat={} cost={} remaining={}",
			new Object[] { character.getName(), statId, cost, character.getStatsPoint()});
	}
}
