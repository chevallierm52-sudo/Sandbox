package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.Main;
import org.dofus.database.Connector;
import org.dofus.objects.characters.breeds.Breed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BreedsData {

	private static final Logger logger = LoggerFactory.getLogger(BreedsData.class);

	private static final ConcurrentMap<Byte, Breed> breeds = new ConcurrentHashMap<>();

	public static void load() {
		Connection conn = Connector.acquire();
		try {
			try(ResultSet reader = conn.createStatement()
					.executeQuery("SELECT * FROM `breed_templates`")) {
				while(reader.next()) {
					Breed breed = new Breed(
							reader.getByte("id"),
							reader.getShort("startLife"),
							reader.getInt("startAP"),
							reader.getInt("startMP"),
							reader.getInt("startProspection"));
					breeds.put(breed.getId(), breed);
					logger.debug("Breed {} loaded", breed.getId());
				}
			}
			logger.info("{} breed(s) loaded", breeds.size());
		} catch(SQLException e) {
			logger.error("Impossible to load breed templates: {}", e.getMessage());
			Main.stop();
		} finally {
			Connector.release(conn);
		}
	}

	public static Breed get(byte id) { return breeds.get(id); }
}
