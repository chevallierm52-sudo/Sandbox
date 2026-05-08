package org.dofus.network.server.handlers;

import org.dofus.database.objects.AccountsData;
import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;

public class NicknameHandler extends ServerClientHandler {

	protected NicknameHandler(Server server, ServerClient client) {
		super(server, client);
		client.getSession().write("AlEr");
	}

	@Override
	public void parse(String packet) throws Exception {
		if(packet.equals("Af"))
			return;
		
		//TODO: Restriction name
		if(!AccountsData.nicknameIsExist(packet)) {
			client.getAccount().setNickname(packet);
			AccountsData.updateNickname(client.getAccount());
			client.setHandler(new ServerChoiceHandler(server, client));
		} else
			client.getSession().write("AlEs");
	}

	@Override
	public void onClosed() {
		System.out.println("NicknameHandler : onClosed()");
	}

}
