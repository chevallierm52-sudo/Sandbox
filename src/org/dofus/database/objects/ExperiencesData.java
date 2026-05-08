package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.dofus.Main;
import org.dofus.database.Connector;
import org.dofus.objects.experiences.Experience;

public class ExperiencesData {

	private static final Connection connection = Connector.getConnection();
	
	//By level - Character
	private static final Map<Short, Experience> character = new HashMap<Short, Experience>();
	
	public static void load() {
		try {
			ResultSet reader = connection
            		.createStatement()
            		.executeQuery("SELECT * FROM experience_templates;");
			
			while(reader.next()) {
				Experience experience = new Experience(
						reader.getShort("level"), 
						reader.getLong("character"), 
						reader.getInt("job"), 
						reader.getInt("mount"), 
						reader.getShort("alignment"));
				
				character.put(experience.getLevel(), experience);
				System.out.println("Experience " + experience.getLevel() + " loaded with success");
			}
			
		} catch(SQLException e) {
			System.out.println("Impossible to load experience templates : " + e.getMessage());
			Main.stop();
		}
	}
	
	public static Experience get(short level) {
		return character.get(level);
	}
}
