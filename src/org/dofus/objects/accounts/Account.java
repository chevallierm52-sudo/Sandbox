package org.dofus.objects.accounts;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.actors.Characters;
import org.dofus.utils.Cipher;

public class Account {

	private int id;
	private String username;
	private String password;
	
	private String question;
	private String answer;
	private String nickname;
	
	private boolean banned = false;
	
	//By id
	private final ConcurrentMap<Integer, Characters> characters = new ConcurrentHashMap<Integer, Characters>();
	
	private IoSession session;
	private boolean connected = false;
	
	public Account(int id, String username, String password, String question, String answer, String nickname, boolean banned) {
		this.setId(id);
		this.setUsername(username);
		this.setPassword(password);
		this.setQuestion(question);
		this.setAnswer(answer);
		this.setNickname(nickname);
		this.setBanned(banned);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public boolean isBanned() {
		return banned;
	}

	public void setBanned(boolean banned) {
		this.banned = banned;
	}

	public ConcurrentMap<Integer, Characters> getCharacters() {
		return characters;
	}
	
	public Characters getCharacterById(int id) {
		if(characters.containsKey(id))
			return characters.get(id);
		return null;
	}
	
	public void addCharacter(Characters character) {
		if(!getCharacters().containsKey(character.getId()))
			getCharacters().put(character.getId(), character);
	}
	
	public void removeCharacter(Characters character) {
		if(getCharacters().containsKey(character.getId()))
			getCharacters().remove(character.getId());
	}
	
	public IoSession getSession() {
		return session;
	}

	public void setSession(IoSession session) {
		this.session = session;
	}

	public boolean valid(String password, String key) {
		return password.equals(Cipher.encode(this.password, key));
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}
}
