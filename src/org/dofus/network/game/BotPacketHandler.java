package org.dofus.network.game;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.session.IoSession;
import org.dofus.network.game.handlers.parsers.PartyParser;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.BotBehavior;
import org.dofus.objects.actors.Characters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traite les packets que le serveur envoie au bot (via BotSession.write).
 * Les bots réagissent directement en appelant les méthodes serveur
 * au lieu de passer par le réseau.
 */
public class BotPacketHandler {

	private static final Logger logger = LoggerFactory.getLogger(BotPacketHandler.class);

	private static final String[] PM_RESPONSES = {
		"Bonjour !",
		"Salut, comment ça va ?",
		"Je suis occupé en ce moment, désolé.",
		"Ah, je vois !",
		"Intéressant...",
		"Tu cherches quelque chose ?",
		"Je repasserai plus tard.",
		"Bonne chance dans ta quête !",
		"Je suis nouveau ici aussi.",
		"On se croise souvent ici !",
	};

	private final BotClient client;

	public BotPacketHandler(BotClient client) {
		this.client = client;
	}

	public void handle(String packet) {
		if(packet == null || packet.isEmpty()) return;

		try {
			if(packet.startsWith("PIK"))       handlePartyInvitation(packet);
			else if(packet.startsWith("cMKF")) handlePrivateMessage(packet);
			// Future: "GA" combat challenge, "Im" info messages, etc.
		} catch(Exception e) {
			logger.warn("Bot {} packet error [{}]: {}",new Object[] {client.getCharacter().getName(), packet, e.getMessage()});
		}
	}

	/** PIK{inviterName}|{botName} — invitation de groupe */
	private void handlePartyInvitation(String packet) {
		// Exemple : "PIKAlyx|Torvan"
		String content = packet.substring(3);
		String[] parts = content.split("\\|");
		if(parts.length < 1) return;

		String inviterName = parts[0];
		Characters bot     = client.getCharacter();

		bot.setInvitation(inviterName);

		// Accepte automatiquement après 1-3 secondes
		long delay = 1 + (long)(Math.random() * 2);
		BotBehavior.schedule(() -> {
			try {
				IoSession botSession = WorldData.getSessionByAccount().get(bot.getOwner());
				PartyParser.accept(bot, botSession);
				logger.debug("Bot {} accepted party from {}", bot.getName(), inviterName);
			} catch(Exception e) {
				logger.warn("Bot {} party accept error: {}", bot.getName(), e.getMessage());
			}
		}, delay, TimeUnit.SECONDS);
	}

	/** cMKF|{senderId}|{senderName}|{message} — message privé reçu */
	private void handlePrivateMessage(String packet) {
		// Répond 40% du temps
		if(Math.random() > 0.40) return;

		String[] parts = packet.split("\\|", 4);
		if(parts.length < 3) return;

		String senderName = parts[2];
		Characters bot     = client.getCharacter();

		// Cherche la session du joueur pour lui répondre
		Characters sender = WorldData.getCharacterByName().get(senderName);
		if(sender == null || !sender.isConnected()) return;

		String response = PM_RESPONSES[(int)(Math.random() * PM_RESPONSES.length)];

		long delay = 2 + (long)(Math.random() * 6);
		BotBehavior.schedule(() -> {
			try {
				IoSession senderSession = WorldData.getSessionByAccount().get(sender.getOwner());
				if(senderSession != null && senderSession.isConnected()) {
					// cMKF = "de {bot} vers {sender}" (réception côté sender)
					senderSession.write("cMKF|" + bot.getId() + "|" + bot.getName() + "|" + response);
					logger.debug("Bot {} replied to {}: {}",new Object[] { bot.getName(), senderName, response});
				}
			} catch(Exception e) {
				logger.warn("Bot {} PM reply error: {}", bot.getName(), e.getMessage());
			}
		}, delay, TimeUnit.SECONDS);
	}
}
