package org.dofus.objects.characters;

public class Emote {

	//TODO : .toString()
	private int emotes;
	
	public Emote(int emotes) {
		this.emotes = emotes;
	}
	 
	public void addEmote(EmoteType emote) {
		emotes += emote.getEmote();
	}
	
	public void removeEmote(EmoteType emote) {
		emotes -= emote.getEmote();
	}

	public int get() {
		return emotes;
	}

	public enum EmoteType {
		SIT(1),
		BYE(2),
		APPLAUSE(4),
		ANGRY(8),
		FEAR(16),
		WEAPON(32),
		FLUTE(64),
		PET(128),
		HELLO(256),
		KISS(512),
		STONE(1024),
		SHEET(2048),
		SCISSORS(4096),
		CROSSARM(8192),
		POINT(16384),
		CROW(32768),
		REST(262144),
		CHAMP(1048576),
		POWERAURA(2097152),
		VAMPYRAURA(4194304);
		
		private int emote;
		
		private EmoteType(int emote) {
			this.emote = emote;
		}
		
		public int getEmote() {
			return emote;
		}
	}
}
