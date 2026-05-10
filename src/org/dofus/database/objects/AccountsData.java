package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.database.Connector;
import org.dofus.objects.accounts.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountsData {

	private static final Logger logger = LoggerFactory.getLogger(AccountsData.class);

	private static final ConcurrentMap<Integer, Account> accounts           = new ConcurrentHashMap<>();
	private static final ConcurrentMap<String, Account>  accountsByUsername = new ConcurrentHashMap<>();
	private static final ConcurrentMap<String, Account>  accountsByKey      = new ConcurrentHashMap<>();

	public static Account load(String username) {
		if(!accountsByUsername.containsKey(username.toLowerCase())) {
			Connection conn = Connector.acquire();
			try {
				PreparedStatement stmt = conn.prepareStatement(
						"SELECT * FROM `accounts` WHERE `username` = ?");
				stmt.setString(1, username);
				try(ResultSet reader = stmt.executeQuery()) {
					while(reader.next()) {
						Account account = new Account(
								reader.getInt("id"),
								reader.getString("username").toLowerCase(),
								reader.getString("password"),
								reader.getString("secret_question"),
								reader.getString("secret_answer"),
								reader.getString("nickname"),
								(reader.getInt("banned") == 1));

						accounts.put(account.getId(), account);
						accountsByUsername.put(account.getUsername(), account);
						logger.debug("Account {} loaded", username);
					}
				} finally {
					stmt.close();
				}
			} catch(SQLException e) {
				logger.error("Impossible to load account {}: {}", username, e.getMessage());
				return null;
			} finally {
				Connector.release(conn);
			}
		}
		return accountsByUsername.get(username.toLowerCase());
	}

	public static boolean nicknameIsExist(String nickname) {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"SELECT `nickname` FROM `accounts` WHERE `nickname` = ?");
			stmt.setString(1, nickname);
			try(ResultSet reader = stmt.executeQuery()) {
				if(reader.next())
					return reader.getString("nickname").equalsIgnoreCase(nickname);
			} finally {
				stmt.close();
			}
		} catch(SQLException e) {
			logger.error("Impossible to check nickname {}: {}", nickname, e.getMessage());
			return true;
		} finally {
			Connector.release(conn);
		}
		return false;
	}

	public static void updateNickname(Account account) throws SQLException {
		if(account == null) return;
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"UPDATE `accounts` SET `nickname` = ? WHERE `id` = ?");
			stmt.setString(1, account.getNickname());
			stmt.setInt(2, account.getId());
			stmt.execute();
			stmt.close();
		} finally {
			Connector.release(conn);
		}
	}

	public static Account getAccountById(int id)         { return accounts.get(id); }
	public static Account getAccountByName(String name)  { return accountsByUsername.get(name); }
	public static Account getAccountByKey(String key)    { return accountsByKey.get(key); }

	public static void addAccountByKey(Account account, String key) { accountsByKey.putIfAbsent(key, account); }
	public static void removeAccountByKey(String key)               { accountsByKey.remove(key); }
}
