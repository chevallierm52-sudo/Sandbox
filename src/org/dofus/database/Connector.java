package org.dofus.database;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Connector {

	private static final Logger logger = LoggerFactory.getLogger(Connector.class);
	private static final int POOL_SIZE = 5;
	private static final BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(POOL_SIZE);
	private static String url;
	private static String dbUsername;
	private static String dbPassword;

	public Connector(String hostname, String username, String password, String database) {
		url      = "jdbc:mysql://" + hostname + ":3306/" + database;
		dbUsername = username;
		dbPassword = password;
		try {
			for(int i = 0; i < POOL_SIZE; i++)
				pool.add(openConnection());
			logger.info("Database pool initialized ({} connections)", POOL_SIZE);
		} catch(SQLException e) {
			throw new RuntimeException("Impossible de se connecter à la base de données : " + e.getMessage(), e);
		}
	}

	private static Connection openConnection() throws SQLException {
		Connection conn = DriverManager.getConnection(url, dbUsername, dbPassword);
		return wrapLogging(conn);
	}

	private static Connection wrapLogging(final Connection conn) {
		return (Connection) Proxy.newProxyInstance(
			Connection.class.getClassLoader(),
			new Class<?>[]{ Connection.class },
			new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					if(method.getName().startsWith("prepareStatement") && args != null && args.length >= 1)
						logger.debug("[SQL] {}", args[0]);
					try {
						return method.invoke(conn, args);
					} catch(java.lang.reflect.InvocationTargetException e) {
						throw e.getCause();
					}
				}
			}
		);
	}

	public static Connection acquire() {
		try {
			Connection conn = pool.poll(5, TimeUnit.SECONDS);
			if(conn == null)
				throw new RuntimeException("Timeout: aucune connexion disponible dans le pool BDD");
			if(conn.isClosed())
				conn = openConnection();
			return conn;
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Thread interrompu en attendant une connexion BDD", e);
		} catch(SQLException e) {
			throw new RuntimeException("Erreur lors de la vérification d'une connexion BDD", e);
		}
	}

	public static void release(Connection conn) {
		if(conn != null)
			pool.offer(conn);
	}

	public void stop() {
		for(Connection conn : pool) {
			try {
				if(!conn.isClosed())
					conn.close();
			} catch(SQLException e) {
				logger.error("Erreur à la fermeture d'une connexion : {}", e.getMessage());
			}
		}
		pool.clear();
		logger.info("Database pool stopped");
	}
}
