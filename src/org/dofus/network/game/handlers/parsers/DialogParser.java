package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.NpcData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.NPC;
import org.dofus.objects.actors.NpcQuestion;
import org.dofus.objects.actors.NpcReply;
import org.dofus.objects.actors.NpcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DialogParser {

	private static final Logger logger = LoggerFactory.getLogger(DialogParser.class);

	// ─── DC{actorId} — le joueur ouvre un dialogue ──────────────────────────────

	public static void create(Characters character, IoSession session, String packet) {
		if(character.getDialogNpc() != null) return; // déjà en dialogue

		int actorId;
		try {
			actorId = Integer.parseInt(packet.substring(2));
		} catch(NumberFormatException e) {
			return;
		}

		NPC npc = character.getCurrentMap().getNpc(actorId);
		if(npc == null) {
			logger.warn("{} a tenté d'ouvrir un dialogue avec un PNJ inconnu (actorId={})",
			            character.getName(), actorId);
			return;
		}

		NpcTemplate tpl = npc.getTemplate();

		// Aucune question configurée → pas de dialogue
		if(tpl.getInitQuestion() <= 0) {
			logger.debug("{} → PNJ '{}' : pas de question initiale, dialogue ignoré",
			             character.getName(), tpl.getName());
			return;
		}

		character.setDialogNpc(tpl);
		// DCK{actorId} : le client Flash affiche le sprite du PNJ via cet ID.
		// Ne pas envoyer DCK|0 (pas de sprite) ni DCK avec un mauvais ID.
		session.write("DCK" + actorId);

		// Lookup global (registre NpcData) : fonctionne même si la question appartient
		// à un autre npc_id en BDD. Fallback sur le mode simplifié si absente.
		NpcQuestion q = NpcData.getQuestion(tpl.getInitQuestion());
		if(q != null) {
			sendQuestion(session, q);
		} else {
			// Mode simplifié : question + réponses stockées directement dans le template
			session.write("DQ" + tpl.getInitQuestion() + "|" + tpl.getQuestionResponses());
		}

		logger.debug("{} a ouvert le dialogue avec '{}'", character.getName(), tpl.getName());
	}

	// ─── DR{replyId} — le joueur choisit une réponse ────────────────────────────

	public static void reply(Characters character, IoSession session, String packet) {
		NpcTemplate tpl = character.getDialogNpc();
		if(tpl == null) return;

		// Le client envoie DR{questionId}|{replyId}
		// (ex: DR3|101 → question 3, réponse 101)
		int replyId;
		try {
			String body = packet.substring(2); // "questionId|replyId"
			int sep = body.indexOf('|');
			String replyPart = (sep >= 0) ? body.substring(sep + 1) : body;
			replyId = Integer.parseInt(replyPart);
		} catch(NumberFormatException e) {
			return;
		}

		// Lookup global : la réponse peut appartenir à n'importe quel template en BDD.
		NpcReply r = NpcData.getReply(replyId);
		if(r == null) {
			// Pas de données de réponse (mode simplifié) → fermeture
			logger.debug("{} a choisi la réponse {} → fermeture (données absentes)",
			             character.getName(), replyId);
			close(character, session);
			return;
		}

		switch(r.getAction()) {
			case NEXT:
				int nextQid = r.getNextQuestionId();
				// Lookup global : la question suivante peut être hors du template courant.
				NpcQuestion next = NpcData.getQuestion(nextQid);
				if(next == null) {
					logger.warn("PNJ '{}' : question id={} introuvable dans le registre global",
					            tpl.getName(), nextQid);
					close(character, session);
				} else {
					sendQuestion(session, next);
				}
				break;

			case SHOP:
				// TODO : ouvrir la boutique PNJ via le protocole d'échange
				session.write("BN");
				break;

			case CLOSE:
			default:
				close(character, session);
				break;
		}

		logger.debug("{} a choisi la réponse {} → {}",
		             new Object[]{character.getName(), replyId, r.getAction()});
	}

	// ─── DV — le joueur ferme la fenêtre de dialogue ────────────────────────────

	public static void quit(Characters character, IoSession session) {
		if(character.getDialogNpc() == null) return;
		close(character, session);
	}

	// ─── Helpers ─────────────────────────────────────────────────────────────────

	private static void sendQuestion(IoSession session, NpcQuestion q) {
		// DQ{questionId}|{replyId1};{replyId2};...
		session.write("DQ" + q.getId() + "|" + q.buildReplyList());
	}

	private static void close(Characters character, IoSession session) {
		character.setDialogNpc(null);
		session.write("DV");
	}
}
