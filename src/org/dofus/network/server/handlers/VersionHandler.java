package org.dofus.network.server.handlers;

import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;

public class VersionHandler extends ServerClientHandler {

	public VersionHandler(Server server, ServerClient client) {
		super(server, client);
	}

	/**
	 * TODO: Faire une classe pour les paquets !
	 */
	@Override
	public void parse(String packet) throws Exception {
		if(packet.equals("1.29.1"))
			client.setHandler(new AuthentificationHandler(server, client));
		else
			client.getSession().write("AlEv" + "1.29.1");
	}

	@Override
	public void onClosed() {
		System.out.println("VersionHandler : onClosed()");
	}

}
