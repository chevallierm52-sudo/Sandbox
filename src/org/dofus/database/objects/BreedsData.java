package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.Main;
import org.dofus.database.Connector;
import org.dofus.objects.characters.breeds.Breed;

public class BreedsData {

	private static final Connection connection = Connector.getConnection();
	
	//By id
	private static final ConcurrentMap<Byte, Breed> breeds = new ConcurrentHashMap<Byte, Breed>();

	public static void load() {
		try {
			ResultSet reader = connection
            		.createStatement()
            		.executeQuery("SELECT * FROM `breed_templates`;");
			
			while(reader.next()) {
				Breed breed = new Breed(
						reader.getByte("id"), 
						reader.getShort("startLife"), 
						reader.getInt("startAP"), 
						reader.getInt("startMP"), 
						reader.getInt("startProspection"));
				
				breeds.put(breed.getId(), breed);
				System.out.println("Breed " + breed.getId() + " loaded with success");
			}
			
		} catch(SQLException e) {
			System.out.println("Impossible to load breed templates : " + e.getMessage());
			Main.stop();
		}
	}
	
	public static Breed get(byte id) {
		return breeds.get(id);
	}
}
