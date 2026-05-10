package org.dofus.objects.actors;

public class NpcReply {

	public enum Action { NEXT, CLOSE, SHOP }

	private final int    id;
	private final String text;
	private final Action action;
	private final String params; // NEXT → nextQuestionId | SHOP → itemId:qty,itemId:qty,...

	public NpcReply(int id, String text, Action action, String params) {
		this.id     = id;
		this.text   = text;
		this.action = action;
		this.params = params;
	}

	public int    getId()     { return id; }
	public String getText()   { return text; }
	public Action getAction() { return action; }
	public String getParams() { return params; }

	public int getNextQuestionId() {
		try { return Integer.parseInt(params); }
		catch(NumberFormatException e) { return -1; }
	}
}
