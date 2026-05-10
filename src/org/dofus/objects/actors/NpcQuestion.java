package org.dofus.objects.actors;

public class NpcQuestion {

	private final int    id;
	private final String text;
	private final int[]  replyIds;

	public NpcQuestion(int id, String text, int[] replyIds) {
		this.id       = id;
		this.text     = text;
		this.replyIds = replyIds;
	}

	public int    getId()      { return id; }
	public String getText()    { return text; }
	public int[]  getReplyIds(){ return replyIds; }

	/** Build the semicolon-separated reply IDs string for the DQ packet. */
	public String buildReplyList() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < replyIds.length; i++) {
			if(i > 0) sb.append(';');
			sb.append(replyIds[i]);
		}
		return sb.toString();
	}
}
