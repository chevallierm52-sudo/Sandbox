package org.dofus.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger de paquets réseau (entrants/sortants).
 *
 * - Affiche chaque paquet sur stdout (comportement historique).
 * - Persiste TOUTES les lignes dans `logs/packets.log` (texte brut, 1 ligne/paquet).
 * - Au démarrage : si `packets.log` existait déjà, il est archivé en
 *   `logs/packets-YYYYMMDD-HHmmss.log` puis un nouveau fichier est créé.
 * - Au shutdown : flush + close via shutdown hook.
 *
 * Format identique entre stdout et fichier :
 *   HH:mm:ss.SSS [RECV|SENT] [Server-N] <packet>
 *
 * Le fichier `logs/packets.log` est toujours le run en cours — facile à lire
 * depuis l'extérieur (Claude, grep, tail -f, etc.).
 */
public class PacketLogger {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final DateTimeFormatter ARCHIVE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Path LOG_DIR = Paths.get("logs");
	private static final Path CURRENT_LOG = LOG_DIR.resolve("packets.log");

	private static final Object FILE_LOCK = new Object();
	private static volatile BufferedWriter writer;
	private static volatile boolean initialized = false;
	private static volatile boolean closed = false;

	private static void ensureInit() {
		if (initialized) return;
		synchronized (FILE_LOCK) {
			if (initialized) return;
			initialized = true;
			try {
				Files.createDirectories(LOG_DIR);
				if (Files.exists(CURRENT_LOG)) {
					String ts = LocalDateTime.now().format(ARCHIVE_FMT);
					Path archive = LOG_DIR.resolve("packets-" + ts + ".log");
					try {
						Files.move(CURRENT_LOG, archive, StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException archiveErr) {
						// Archive impossible (fichier verrouillé ?) : on écrase juste.
					}
				}
				writer = Files.newBufferedWriter(CURRENT_LOG,
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE);
				writer.write("# PacketLog start " + LocalDateTime.now() + System.lineSeparator());
				writer.flush();

				Runtime.getRuntime().addShutdownHook(new Thread(PacketLogger::shutdown, "packet-log-shutdown"));
			} catch (IOException e) {
				System.err.println("[PacketLogger] init failed: " + e.getMessage());
				writer = null;
			}
		}
	}

	private static void shutdown() {
		synchronized (FILE_LOCK) {
			if (closed) return;
			closed = true;
			if (writer == null) return;
			try {
				writer.write("# PacketLog end " + LocalDateTime.now() + System.lineSeparator());
				writer.flush();
				writer.close();
			} catch (IOException ignored) {
			} finally {
				writer = null;
			}
		}
	}

	public static void recv(String server, long sessionId, Object packet) {
		write("RECV", server, sessionId, packet);
	}

	public static void sent(String server, long sessionId, Object packet) {
		write("SENT", server, sessionId, packet);
	}

	private static void write(String direction, String server, long sessionId, Object packet) {
		String line = LocalTime.now().format(FMT) + " [" + direction + "] [" + server + "-" + sessionId + "] " + packet;
		System.out.println(line);
		ensureInit();
		BufferedWriter w = writer;
		if (w == null || closed) return;
		synchronized (FILE_LOCK) {
			if (writer == null || closed) return;
			try {
				writer.write(line);
				writer.newLine();
				writer.flush();
			} catch (IOException ignored) {
				// Ne pas casser le serveur si IO lent ; perdre la ligne silencieusement.
			}
		}
	}
}
