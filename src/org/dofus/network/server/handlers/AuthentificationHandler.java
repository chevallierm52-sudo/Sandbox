package org.dofus.network.server.handlers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.AccountsData;
import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;

public class AuthentificationHandler extends ServerClientHandler {

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
				
				session.write("AlEa");
				session.close(true);
				
				client.getSession().write("AlEd"); 
				client.getSession().close(true);
			}
		} else { //XXX Account null
			client.getSession().write("AlEp");
		}
	}

	@Override
	public void onClosed() {
		System.out.println("AuthentificationHandler : onClosed()");
	}

}
