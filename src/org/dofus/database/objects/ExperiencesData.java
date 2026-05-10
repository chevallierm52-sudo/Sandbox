package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.dofus.Main;
import org.dofus.database.Connector;
import org.dofus.objects.experiences.Experience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExperiencesData {

	private static final Logger logger = LoggerFactory.getLogger(ExperiencesData.class);

	private static final Map<Short, Experience> character = new HashMap<>();

	public static void load() {
		Connection conn = Connector.acquire();
		try {
			try(ResultSet reader = conn.createStatement()
					.executeQuery("SELECT * FROM experience_templates")) {
				while(reader.next()) {
					Experience experience = new Experience(
							reader.getShort("level"),
							reader.getLong("character"),
							reader.getInt("job"),
							reader.getInt("mount"),
							reader.getShort("alignment"));
					character.put(experience.getLevel(), experience);
				}
			}
			logger.info("{} experience level(s) loaded", character.size());
		} catch(SQLException e) {
			logger.error("Impossible to load experience templates: {}", e.getMessage());
			Main.stop();
		} finally {
			Connector.release(conn);
		}
	}

	public static Experience get(short level) { return character.get(level); }
}
