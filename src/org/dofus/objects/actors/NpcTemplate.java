package org.dofus.objects.actors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modèle de PNJ : apparence visuelle + arbre de dialogue.
 * Les champs correspondent exactement aux colonnes de la table `npc_templates`.
 */
public class NpcTemplate {

	// ── Identité ────────────────────────────────────────────────────────────────
	private final int    id;
	private final String name;

	// ── Apparence visuelle (packet GM) ──────────────────────────────────────────
	private final int    gfxID;
	private final int    scaleX;
	private final int    scaleY;
	private final int    sex;
	private final int    color1;
	private final int    color2;
	private final int    color3;
	private final String accessories;   // ex. "0,0,0,0,0" ou "0,2010,0,0,0"
	private final int    extraClip;
	private final int    customArtWork;

	// ── Dialogue ─────────────────────────────────────────────────────────────────
	private final int    initQuestion;      // ID de la question d'accueil (-1 = pas de dialogue)
	private final String questionResponses; // IDs de réponses pour la question initiale ("101;102;103")

	// ── Arbre de dialogue complet (chargé depuis npc_questions / npc_replies) ───
	private final Map<Integer, NpcQuestion> questions = new ConcurrentHashMap<>();
	private final Map<Integer, NpcReply>    replies   = new ConcurrentHashMap<>();

	// ────────────────────────────────────────────────────────────────────────────

	public NpcTemplate(int id, String name,
	                   int gfxID, int scaleX, int scaleY,
	                   int sex, int color1, int color2, int color3,
	                   String accessories, int extraClip, int customArtWork,
	                   int initQuestion, String questionResponses) {
		this.id                = id;
		this.name              = name;
		this.gfxID             = gfxID;
		this.scaleX            = scaleX;
		this.scaleY            = scaleY;
		this.sex               = sex;
		this.color1            = color1;
		this.color2            = color2;
		this.color3            = color3;
		this.accessories       = accessories  == null ? "" : accessories;
		this.extraClip         = extraClip;
		this.customArtWork     = customArtWork;
		this.initQuestion      = initQuestion;
		this.questionResponses = questionResponses == null ? "" : questionResponses;
	}

	// ── Getters ──────────────────────────────────────────────────────────────────

	public int    getId()               { return id; }
	public String getName()             { return name; }
	public int    getGfxID()            { return gfxID; }
	public int    getScaleX()           { return scaleX; }
	public int    getScaleY()           { return scaleY; }
	public int    getSex()              { return sex; }
	public int    getColor1()           { return color1; }
	public int    getColor2()           { return color2; }
	public int    getColor3()           { return color3; }
	public String getAccessories()      { return accessories; }
	public int    getExtraClip()        { return extraClip; }
	public int    getCustomArtWork()    { return customArtWork; }
	public int    getInitQuestion()     { return initQuestion; }
	public String getQuestionResponses(){ return questionResponses; }

	// ── Dialogue ─────────────────────────────────────────────────────────────────

	public Map<Integer, NpcQuestion> getQuestions() { return questions; }
	public Map<Integer, NpcReply>    getReplies()   { return replies; }

	public NpcQuestion getQuestion(int questionId) { return questions.get(questionId); }
	public NpcReply    getReply(int replyId)       { return replies.get(replyId); }

	public void addQuestion(NpcQuestion q) { questions.put(q.getId(), q); }
	public void addReply(NpcReply r)       { replies.put(r.getId(), r); }

	// ── Helpers packet ───────────────────────────────────────────────────────────

	/**
	 * Convertit le champ `accessories` (décimaux séparés par virgule)
	 * en chaîne hexadécimale pour le packet GM.
	 * Exemple : "0,2010,0,0,0" → ",7DA,,,"
	 */
	public String buildAccessories() {
		if(accessories.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		String[] parts = accessories.split(",");
		for(int i = 0; i < parts.length; i++) {
			if(i > 0) sb.append(',');
			try {
				int v = Integer.parseInt(parts[i].trim());
				if(v != 0) sb.append(Integer.toHexString(v).toUpperCase());
			} catch(NumberFormatException ignored) { }
		}
		return sb.toString();
	}
}
