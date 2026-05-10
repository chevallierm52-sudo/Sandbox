package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dofus.database.Connector;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.actors.NPC;
import org.dofus.objects.actors.NpcQuestion;
import org.dofus.objects.actors.NpcReply;
import org.dofus.objects.actors.NpcReply.Action;
import org.dofus.objects.actors.NpcTemplate;
import org.dofus.objects.maps.MapTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpcData {

	private static final Logger logger = LoggerFactory.getLogger(NpcData.class);

	/** Tous les templates chargés, indexés par ID de template. */
	private static final ConcurrentMap<Integer, NpcTemplate> templates = new ConcurrentHashMap<>();

	/**
	 * Questions et réponses globales (comme World.getNPCQuestion() dans AncestraRemake).
	 * En Dofus 1.29, une réponse peut enchaîner vers n'importe quelle question,
	 * quel que soit le template qui la "possède" en BDD.
	 * Les stocker par template uniquement empêche la navigation multi-niveaux.
	 */
	private static final ConcurrentMap<Integer, NpcQuestion> allQuestions = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Integer, NpcReply>    allReplies   = new ConcurrentHashMap<>();

	/** Compteur global pour générer des IDs de spawn uniques. */
	private static final AtomicInteger spawnCounter = new AtomicInteger(1);

	/** Pattern pour parser le JSON embarqué des spawns : {"mapid":X,"cellid":Y,"orientation":Z} */
	private static final Pattern SPAWN_PATTERN =
		Pattern.compile("\"mapid\":(\\d+),\"cellid\":(\\d+),\"orientation\":(\\d+)");

	// ────────────────────────────────────────────────────────────────────────────

	public static void load() {
		templates.clear();
		allQuestions.clear();
		allReplies.clear();
		spawnCounter.set(1);

		int spawns = loadTemplates();           // templates + spawns embarqués
		loadQuestions();                        // arbre de dialogue (table optionnelle)
		loadReplies();                          // actions de réponse (table optionnelle)

		logger.info("{} templates PNJ chargés, {} spawn(s) placé(s) sur les cartes",
		            templates.size(), spawns);
	}

	// ─── Chargement des templates ────────────────────────────────────────────────

	/**
	 * Lit la table `npc_templates` (nouveau schéma), instancie chaque NpcTemplate
	 * et parse directement les spawns depuis le champ `spawns_json`.
	 * @return nombre total de spawns placés
	 */
	private static int loadTemplates() {
		int totalSpawns = 0;
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
				"SELECT `id`, `name`, `gfxID`, `scaleX`, `scaleY`, `sex`," +
				"       `color1`, `color2`, `color3`, `accessories`," +
				"       `extraClip`, `customArtWork`, `initQuestion`," +
				"       `question_responses`, `spawns_json`" +
				" FROM `npc_templates`");
			try(ResultSet rs = stmt.executeQuery()) {
				while(rs.next()) {
					NpcTemplate tpl = new NpcTemplate(
						rs.getInt   ("id"),
						rs.getString("name"),
						rs.getInt   ("gfxID"),
						rs.getInt   ("scaleX"),
						rs.getInt   ("scaleY"),
						rs.getInt   ("sex"),
						rs.getInt   ("color1"),
						rs.getInt   ("color2"),
						rs.getInt   ("color3"),
						rs.getString("accessories"),
						rs.getInt   ("extraClip"),
						rs.getInt   ("customArtWork"),
						rs.getInt   ("initQuestion"),
						rs.getString("question_responses")
					);
					templates.put(tpl.getId(), tpl);

					// Spawns embarqués en JSON dans la même ligne
					String json = rs.getString("spawns_json");
					if(json != null && !json.trim().isEmpty()) {
						totalSpawns += parseAndSpawn(tpl, json);
					}
				}
			} finally { stmt.close(); }
		} catch(SQLException e) {
			logger.error("loadTemplates PNJ : {}", e.getMessage());
		} finally {
			Connector.release(conn);
		}
		return totalSpawns;
	}

	/**
	 * Parse le champ `spawns_json` d'un template et place les PNJ sur leurs cartes.
	 * Format attendu : [{"mapid":753,"cellid":238,"orientation":1}, ...]
	 */
	private static int parseAndSpawn(NpcTemplate tpl, String json) {
		int count = 0;
		Matcher m = SPAWN_PATTERN.matcher(json);
		while(m.find()) {
			int mapId  = Integer.parseInt(m.group(1));
			int cellId = Integer.parseInt(m.group(2));
			int dir    = Integer.parseInt(m.group(3));

			MapTemplate map = MapsData.findById(mapId);
			if(map == null) {
				logger.warn("Spawn JSON : carte {} inconnue pour le PNJ '{}'", mapId, tpl.getName());
				continue;
			}

			EOrientation orient = EOrientation.valueOf(dir);
			if(orient == null) orient = EOrientation.valueOf(1); // par défaut : SE

			int spawnId = spawnCounter.getAndIncrement();
			NPC npc = new NPC(spawnId, tpl, map, (short) cellId, orient);
			map.addNpc(npc);
			count++;
		}
		return count;
	}

	// ─── Chargement optionnel de l'arbre de dialogue ─────────────────────────────

	/**
	 * Tente de charger la table `npc_questions`.
	 * Si elle n'existe pas, un simple avertissement est loggé (non bloquant).
	 */
	private static void loadQuestions() {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `npc_questions`");
			try(ResultSet rs = stmt.executeQuery()) {
				int count = 0;
				while(rs.next()) {
					int    npcId  = rs.getInt("npc_id");
					int    qId    = rs.getInt("id");
					String text   = rs.getString("text");
					String rawIds = rs.getString("replies");

					NpcQuestion q = new NpcQuestion(qId, text, parseIds(rawIds));

					// Registre global : toutes les questions sont accessibles par ID,
					// quel que soit le template qui les "possède" (navigation multi-niveaux).
					allQuestions.put(qId, q);

					// Aussi dans le template d'appartenance (pour initQuestion)
					NpcTemplate tpl = templates.get(npcId);
					if(tpl != null) tpl.addQuestion(q);

					count++;
				}
				logger.debug("{} question(s) de dialogue chargée(s) (registre global)", count);
			} finally { stmt.close(); }
		} catch(SQLException e) {
			logger.warn("Table npc_questions absente ou erreur (dialogue simplifié) : {}", e.getMessage());
		} finally {
			Connector.release(conn);
		}
	}

	/**
	 * Tente de charger la table `npc_replies`.
	 * Si elle n'existe pas, un simple avertissement est loggé (non bloquant).
	 */
	private static void loadReplies() {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `npc_replies`");
			try(ResultSet rs = stmt.executeQuery()) {
				int count = 0;
				while(rs.next()) {
					int    npcId  = rs.getInt("npc_id");
					int    rId    = rs.getInt("id");
					String text   = rs.getString("text");
					String action = rs.getString("action").toUpperCase();
					String params = rs.getString("params");

					Action a;
					try { a = Action.valueOf(action); }
					catch(IllegalArgumentException ex) { a = Action.CLOSE; }

					NpcReply r = new NpcReply(rId, text, a, params == null ? "" : params);

					// Registre global : toutes les réponses accessibles par ID.
					allReplies.put(rId, r);

					// Aussi dans le template d'appartenance
					NpcTemplate tpl = templates.get(npcId);
					if(tpl != null) tpl.addReply(r);

					count++;
				}
				logger.debug("{} réponse(s) de dialogue chargée(s) (registre global)", count);
			} finally { stmt.close(); }
		} catch(SQLException e) {
			logger.warn("Table npc_replies absente ou erreur (dialogue simplifié) : {}", e.getMessage());
		} finally {
			Connector.release(conn);
		}
	}

	// ─── Utilitaires ──────────────────────────────────────────────────────────────

	public static NpcTemplate getTemplate(int id) {
		return templates.get(id);
	}

	/** Lookup global d'une question par ID (indépendant du template). */
	public static NpcQuestion getQuestion(int id) {
		return allQuestions.get(id);
	}

	/** Lookup global d'une réponse par ID (indépendant du template). */
	public static NpcReply getReply(int id) {
		return allReplies.get(id);
	}

	/** Parse une chaîne d'IDs séparés par ";" en tableau int[]. */
	private static int[] parseIds(String raw) {
		if(raw == null || raw.trim().isEmpty()) return new int[0];
		String[] parts = raw.split(";");
		int[] ids = new int[parts.length];
		int count = 0;
		for(String p : parts) {
			try { ids[count++] = Integer.parseInt(p.trim()); }
			catch(NumberFormatException ignored) { }
		}
		int[] result = new int[count];
		System.arraycopy(ids, 0, result, 0, count);
		return result;
	}
}
