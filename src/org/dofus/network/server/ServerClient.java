package org.dofus.network.server;

import org.apache.mina.core.session.IoSession;
import org.dofus.objects.accounts.Account;
import org.dofus.utils.StringUtils;

public class ServerClient {

	private IoSession session;
	private ServerClientHandler handler; 
	
	private final String key;
	
	private Account account;
	
	public ServerClient(IoSession session) {
		setSession(session);
		
		this.key = StringUtils.random(32);
		this.getSession().write("HC" + getKey());
	}

	public IoSession getSession() {
		return session;
	}

	public void setSession(IoSession session) {
		this.session = session;
	}
	
	public ServerClientHandler getHandler() {
		return handler;
	}
	
	public void setHandler(ServerClientHandler handler) {
		this.handler = handler;
	}

	public String getKey() {
		return key;
	}

	public Account getAccount() {
		return account;
	}
	
	public void setAccount(Account account) {
		this.account = account;
	}
}
