package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.characters.breeds.Breed.BreedType;

public class BoostParser {

	public static void boost(Characters character, IoSession session, int packet) {
		/** FIXME: Finish
		 * Ancestra aussi faire plus propre
    	 * Créer un timer dès le premier boost stat et sauvegarder au bout de 3min
    	 * ca evite de save à chaque boost
    	 */
		int value = 0;
		Characters player = character;
		switch(packet) {
			case 10://Force
				value = player.getStats().getEffect(EConstants.ADD_STRENGTH.getInt());
			break;
			case 13://Chance
				value = player.getStats().getEffect(EConstants.ADD_CHANCE.getInt());
			break;
			case 14://Agilit�
				value = player.getStats().getEffect(EConstants.ADD_AGILITY.getInt());
			break;
			case 15://Intelligence
				value = player.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt());
			break;
		}
		
		int cout = Statistic.getReqPtsToBoostStatsByClass(player.getBreed().getId(), packet, value);
		
		if(cout <= player.getStatsPoint()) {
			switch(packet) {
				case 11://Vita
					int val = 1;
					if(player.getBreed().getId() == BreedType.SACRIEUR.getValue())
						val = 2;
					
					player.getStats().add(EConstants.ADD_VITALITY.getInt(), val);
				break;
				case 12://Sage
					player.getStats().add(EConstants.ADD_WISDOM.getInt(), 1);
				break;
				case 10://Force
					player.getStats().add(EConstants.ADD_STRENGTH.getInt(), 1);
				break;
				case 13://Chance
					player.getStats().add(EConstants.ADD_CHANCE.getInt(), 1);
				break;
				case 14://Agilit�
					player.getStats().add(EConstants.ADD_AGILITY.getInt(), 1);
				break;
				case 15://Intelligence
					player.getStats().add(EConstants.ADD_INTELLIGENCE.getInt(), 1);
				break;
				default:
					return;
			}
			
			player.setStatsPoint((short) (player.getStatsPoint() - cout));
			session.write(Statistic.getStatisticsMessage(player));
		}
	}
}
