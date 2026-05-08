package org.dofus;

import java.io.IOException;

import org.dofus.constants.EApplication;
import org.dofus.database.Connector;
import org.dofus.database.Initialisation;
import org.dofus.network.game.Game;
import org.dofus.network.server.Server;
import org.dofus.objects.actors.BotAI;

public class Main {

	public static Connector connector;
	
	static Server server = new Server();
	static Game game = new Game();
	
	public static void main(String[] args) throws IOException {
		long time = System.currentTimeMillis();
		System.out.println("Dofus 1.29.1 sandbox " + EApplication.APPLICATION_VERSION.getsValue() + "\n");
		
		connector = new Connector("127.0.0.1", "root", "", "Dofus");
		server.start((short) 499);
		game.start((short) 5555);
		
		Initialisation.init();
		try {
			BotAI.botAI("BOT", 7411, (short) 250);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Sandbox loaded in "+ ((System.currentTimeMillis() - time) / 1000) + " seconds.\n");
		System.in.read();
		
		game.stop();
		server.stop();
		connector.stop();
	}
	
	public static void stop() {
		game.stop();
		server.stop();
		//FIXME: Attendre que toute les requêtes soit executer avant de close !
		connector.stop();
	}
}
