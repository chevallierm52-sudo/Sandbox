package org.dofus.network.game;

import java.util.Stack;

import org.apache.mina.core.session.IoSession;
import org.dofus.game.actions.IGameAction;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.actors.Characters;

public class GameClient {

	private Game game;
	private IoSession session;
	
	private GameClientHandler handler;
	
	private Account account;
	private Characters characters;
	
	private Stack<IGameAction> actions = new Stack<>();
	
	public GameClient(Game game, IoSession session) {
		setGame(game);
		setSession(session);
		
		this.getSession().write("HG");
	}

	public IoSession getSession() {
		return session;
	}

	public Game getGame() {
		return game;
	}

	public void setGame(Game game) {
		this.game = game;
	}

	public void setSession(IoSession session) {
		this.session = session;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Characters getCharacter() {
		return characters;
	}

	public void setCharacters(Characters characters) {
		this.characters = characters;
	}

	public Stack<IGameAction> getActions() {
		return actions;
	}

	public void setActions(Stack<IGameAction> actions) {
		this.actions = actions;
	}
	
    public boolean isBusy() {
        return actions.size() > 0;
    }

	public GameClientHandler getHandler() {
		return handler;
	}

	public void setHandler(GameClientHandler handler) {
		this.handler = handler;
	}
}
