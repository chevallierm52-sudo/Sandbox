package org.dofus.network.server.handlers;

import org.dofus.network.server.Server;
import org.dofus.network.server.ServerClient;
import org.dofus.network.server.ServerClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionHandler extends ServerClientHandler {

	private static final Logger logger = LoggerFactory.getLogger(VersionHandler.class);

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
		logger.debug("VersionHandler closed");
	}

}
