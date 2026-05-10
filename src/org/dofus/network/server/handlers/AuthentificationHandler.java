package org.dofus.network.server.handlers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.AccountsData;
import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthentificationHandler extends ServerClientHandler {

	private static final Logger logger = LoggerFactory.getLogger(AuthentificationHandler.class);

	protected AuthentificationHandler(Server server, ServerClient client) {
		super(server, client);
	}

	@Override
	public void parse(String packet) throws Exception {
		String[] data = packet.split("\n");
		
		Account account = AccountsData.load(data[0].trim());
		
		if(account != null) { //XXX Account is loaded
			if(!account.isConnected()) { //XXX Account not connected
				if(!account.isBanned()) { //XXX Account not banned
					if(account.valid(data[1].trim(), client.getKey())) { //XXX Good login
						client.setAccount(account);
						client.getAccount().setConnected(true);
						client.setHandler(account.getNickname().isEmpty() ? new NicknameHandler(server, client) : new ServerChoiceHandler(server, client));
					} else { //XXX Bad login
						client.getSession().write("AlEf");
					}
				} else { //XXX Account banned
					client.getSession().write("AlEb");
				}
			} else { //IoSession = Account already connected
				IoSession session = WorldData.getSessionByAccount().get(account);

				if(session != null && session.isConnected()) {
					// Kick the existing live session, then reject the new one
					session.write("AlEa");
					session.close(true);
					client.getSession().write("AlEd");
					client.getSession().close(true);
				} else {
					// Stale connected flag (crash / network drop — session already gone)
					account.setConnected(false);
					WorldData.removeSessionByAccount(account);
					// Re-attempt login with the new session
					if(!account.isBanned()) {
						if(account.valid(data[1].trim(), client.getKey())) {
							client.setAccount(account);
							client.getAccount().setConnected(true);
							client.setHandler(account.getNickname().isEmpty() ?
								new NicknameHandler(server, client) :
								new ServerChoiceHandler(server, client));
						} else {
							client.getSession().write("AlEf");
						}
					} else {
						client.getSession().write("AlEb");
					}
				}
			}
		} else { //XXX Account null
			client.getSession().write("AlEp");
		}
	}

	@Override
	public void onClosed() {
		logger.debug("AuthentificationHandler closed");
	}

}
