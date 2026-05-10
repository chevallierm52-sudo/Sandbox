package org.dofus.network.game;

import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;

/**
 * Client complet pour les bots : étend GameClient pour être compatible
 * avec tout le code existant (instanceof, getCharacter(), getSession()...).
 *
 * BotSession remplace la vraie connexion MINA — les packets serveur→bot
 * sont routés vers BotPacketHandler au lieu du réseau.
 *
 * Enregistré dans WorldData.controllers ET WorldData.sessionByAccount
 * → le bot est vu comme un joueur connecté par toutes les boucles de broadcast.
 */
public class BotClient extends GameClient {

	public BotClient(Characters character) {
		super(null, new BotSession(character.getId()));
		// super() appelle session.write("HG") → BotSession l'ignore silencieusement

		setAccount(character.getOwner());
		setCharacters(character);

		// Attacher le handler de packets au bot session
		BotSession botSession = (BotSession) getSession();
		botSession.setPacketHandler(new BotPacketHandler(this));
	}

	/**
	 * Enregistre ce BotClient dans WorldData.
	 * Après cet appel, toutes les boucles broadcast incluent le bot.
	 */
	public void register() {
		WorldData.addSessionByAccount(getAccount(), getSession());
		WorldData.addController(getCharacter().getId(), this);
	}
}
