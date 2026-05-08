package org.dofus.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Connector {

	public static Connection connector;
	
	public Connector(String hostname, String username, String password, String database) {
		try {
			setConnection(DriverManager.getConnection("jdbc:mysql://"
					+ hostname + ":"
					+ 3306 + "/"
					+ database,
					username,
					password));
			System.out.println("Connected to database");
		} catch(SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	public void stop() {
		try {
			if(!getConnection().isClosed()) {
				getConnection().close();
				System.out.println("Database successfully stoped");
			}
		} catch(SQLException e) {
			System.out.println("Impossible to close the database : " + e.getMessage());
		}
	}
	
	public static Connection getConnection() {
		return connector;
	}
	
	public void setConnection(Connection connection) {
		connector = connection;
	}
}
