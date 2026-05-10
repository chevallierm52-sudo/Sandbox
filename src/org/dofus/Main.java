package org.dofus;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.dofus.constants.EApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dofus.database.Connector;
import org.dofus.database.Initialisation;
import org.dofus.network.game.Game;
import org.dofus.network.server.Server;
import org.dofus.objects.actors.BotAI;
import org.dofus.objects.actors.BotAIService;
import org.dofus.objects.actors.BotBehavior;
import org.dofus.objects.actors.BotLearning;
import org.dofus.game.fight.DropTable;
import org.dofus.utils.DeferredSaveService;
import org.dofus.utils.MapRespawnService;
import org.dofus.utils.RegenService;

public class Main {

	static {
		System.setProperty("org.slf4j.simpleLogger.showDateTime",     "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat",   "HH:mm:ss.SSS");
		System.setProperty("org.slf4j.simpleLogger.levelInBrackets",  "true");
		System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",  "info");
	}

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static Connector connector;

	static Server server = new Server();
	static Game game = new Game();

	public static void main(String[] args) throws IOException {
		long time = System.currentTimeMillis();
		logger.info("Dofus 1.29.1 sandbox {}", EApplication.APPLICATION_VERSION.getsValue());

		Properties config = new Properties();
		try(FileInputStream fis = new FileInputStream("config.properties")) {
			config.load(fis);
		} catch(IOException e) {
			logger.warn("config.properties introuvable, utilisation des valeurs par défaut.");
		}

		String dbHost = config.getProperty("db.host", "127.0.0.1");
		String dbUser = config.getProperty("db.username", "root");
		String dbPass = config.getProperty("db.password", "");
		String dbName = config.getProperty("db.name", "Dofus");
		short loginPort = Short.parseShort(config.getProperty("server.login.port", "499"));
		short gamePort  = Short.parseShort(config.getProperty("server.game.port", "5555"));

		Runtime.getRuntime().addShutdownHook(new Thread(Main::stop, "shutdown-hook"));

		connector = new Connector(dbHost, dbUser, dbPass, dbName);
		server.start(loginPort);
		game.start(gamePort);

		Initialisation.init();

		// ── Drops par défaut si pas de table SQL monster_drops ─────────────────
		DropTable.loadDefaults();

		// ── Système IA bots ────────────────────────────────────────────────────
		BotAIService.configure(config);   // configure OpenAI (désactivé par défaut)
		BotLearning.init();               // charge bot_memory.csv
		try {
			BotAI.spawnAll();
		} catch(Exception e) {
			logger.error("Failed to spawn bots: {}", e.getMessage());
		}

		logger.info("Sandbox loaded in {} seconds.", (System.currentTimeMillis() - time) / 1000);
		System.in.read();
	}

	public static void stop() {
		BotLearning.shutdown();
		BotBehavior.shutdown();
		DeferredSaveService.shutdown();
		MapRespawnService.shutdown();
		RegenService.shutdown();
		game.stop();
		server.stop();
		connector.stop();
	}
}
