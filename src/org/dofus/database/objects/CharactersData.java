package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.constants.EConstants;
import org.dofus.database.Connector;
import org.dofus.objects.WorldData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.characters.Restriction;
import org.dofus.objects.characters.Right;
import org.dofus.objects.experiences.AlignmentExperience;
import org.dofus.objects.experiences.CharacterExperience;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.maps.MapTemplate;

public class CharactersData {

	private static final Logger logger = LoggerFactory.getLogger(CharactersData.class);

	private static final ConcurrentMap<Integer, Characters> characters = new ConcurrentHashMap<>();
	private static volatile boolean saveColumnsReady = false;

	public static void load(Account account) {
		Connection conn = Connector.acquire();
		try {
			ensureSaveColumns(conn);
			PreparedStatement loadStmt = conn.prepareStatement(
					"SELECT * FROM `characters` WHERE `owner` = ?");
			loadStmt.setInt(1, account.getId());
			try (ResultSet reader = loadStmt.executeQuery()) {
				ConcurrentMap<Integer, Integer> statistics;

				while (reader.next()) {
					MapTemplate map = MapsData.load(reader.getShort("currentMap"));

					statistics = new ConcurrentHashMap<>();
					statistics.put(EConstants.ADD_VITALITY.getInt(), reader.getInt("vitality"));
					statistics.put(EConstants.ADD_STRENGTH.getInt(), reader.getInt("strength"));
					statistics.put(EConstants.ADD_WISDOM.getInt(), reader.getInt("wisdom"));
					statistics.put(EConstants.ADD_INTELLIGENCE.getInt(), reader.getInt("intelligence"));
					statistics.put(EConstants.ADD_CHANCE.getInt(), reader.getInt("chance"));
					statistics.put(EConstants.ADD_AGILITY.getInt(), reader.getInt("agility"));

					Characters character = new Characters(
							reader.getInt("id"),
							account,
							reader.getString("name"),
							BreedsData.get(reader.getByte("breed")),
							reader.getByte("gender"),
							reader.getInt("color1"),
							reader.getInt("color2"),
							reader.getInt("color3"),
							reader.getShort("skin"),
							reader.getShort("size"),
							map,
							reader.getShort("currentCell"),
							EOrientation.valueOf(reader.getInt("currentOrientation")),
							new Right(reader.getInt("rights")),
							new Restriction(reader.getInt("restrictions")),
							reader.getShort("life"),
							reader.getShort("energy"),
							null,
							reader.getLong("kamas"),
							statistics,
							reader.getShort("statsPoint"),
							reader.getShort("spellPoint"),
							reader.getByte("alignment"),
							null,
							(reader.getInt("showWings") == 1));

					character.setExperience(new CharacterExperience(
							reader.getShort("level"),
							reader.getLong("experience"),
							ExperiencesData.get(reader.getShort("level")),
							character));

					int saveMap = optionalInt(reader, "saveMap",
							character.getCurrentMap() != null ? character.getCurrentMap().getId() : 0);
					short saveCell = (short) optionalInt(reader, "saveCell", character.getCurrentCell());
					character.setSaveMap(saveMap);
					character.setSaveCell(saveCell);

					character.setAlignment(new AlignmentExperience(
							reader.getShort("grade"),
							reader.getLong("honor"),
							reader.getByte("dishonor"),
							ExperiencesData.get(reader.getShort("grade")),
							character));

					// Chargement de l'inventaire
					java.util.List<Item> charItems = ItemsData.loadForCharacter(character.getId());
					if (!charItems.isEmpty()) {
						Inventory inv = new Inventory();
						inv.load(charItems);
						character.setInventory(inv);
					}

					characters.put(character.getId(), character);
					account.addCharacter(character);

					logger.debug("Character {} (owner {}) loaded with {} items",
							new Object[] { character.getName(), character.getOwner().getId(), charItems.size() });
				}
			} finally {
				loadStmt.close();
			}
		} catch (SQLException e) {
			logger.error("Impossible to load characters for account {}: {}", account.getId(), e.getMessage());
		} finally {
			Connector.release(conn);
		}
	}

	public static void create(Characters character, int owner) throws SQLException {
		Connection conn = Connector.acquire();
		try {
			ensureSaveColumns(conn);
			PreparedStatement stmt = conn.prepareStatement(
					"INSERT INTO `characters`(`id`,`owner`,`name`,`breed`,`gender`,`color1`,`color2`,`color3`," +
							"`skin`,`size`,`currentMap`,`currentCell`,`saveMap`,`saveCell`,`life`) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

			stmt.setInt(1, character.getId());
			stmt.setInt(2, owner);
			stmt.setString(3, character.getName());
			stmt.setByte(4, character.getBreedId());
			stmt.setByte(5, character.getGender());
			stmt.setInt(6, character.getColor1());
			stmt.setInt(7, character.getColor2());
			stmt.setInt(8, character.getColor3());
			stmt.setShort(9, character.getSkin());
			stmt.setShort(10, character.getSize());
			stmt.setInt(11, character.getCurrentMap().getId());
			stmt.setShort(12, character.getCurrentCell());
			stmt.setInt(13, character.getSaveMap());
			stmt.setShort(14, character.getSaveCell());
			stmt.setInt(15, character.getLifeMax());
			stmt.execute();
			stmt.close();
		} finally {
			Connector.release(conn);
		}

		MapsData.load(character.getCurrentMap().getId());
		WorldData.addCharacterById(character, character.getId());
		characters.put(character.getId(), character);
	}

	public static void delete(Characters character) {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"DELETE FROM `characters` WHERE `id` = ?");
			stmt.setInt(1, character.getId());
			stmt.execute();
			stmt.close();
			removeCharacter(character);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			Connector.release(conn);
		}
	}

	public static void update(Characters character) {
		Connection conn = Connector.acquire();
		try {
			ensureSaveColumns(conn);
			PreparedStatement stmt = conn.prepareStatement(
					"UPDATE `characters` SET " +
							"`currentMap`=?,`currentCell`=?,`currentOrientation`=?,`saveMap`=?,`saveCell`=?,`rights`=?,`restrictions`=?,"
							+
							"`channels`=?,`emotes`=?,`life`=?,`energy`=?,`level`=?,`experience`=?,`kamas`=?," +
							"`vitality`=?,`strength`=?,`wisdom`=?,`intelligence`=?,`chance`=?,`agility`=?," +
							"`statsPoint`=?,`spells`=?,`spellPoint`=?,`alignment`=?,`grade`=?,`honor`=?," +
							"`dishonor`=?,`showWings`=?,`connected`=? WHERE `id`=?");

			stmt.setInt(1, character.getCurrentMap().getId());
			stmt.setInt(2, character.getCurrentCell());
			stmt.setInt(3, character.getOrientation().ordinal());
			stmt.setInt(4, character.getSaveMap());
			stmt.setInt(5, character.getSaveCell());
			stmt.setInt(6, character.getRight().get());
			stmt.setInt(7, character.getRestriction().get());
			stmt.setString(8, character.getChannels());
			stmt.setString(9, "");
			stmt.setInt(10, character.getLife());
			stmt.setInt(11, character.getEnergy());
			stmt.setInt(12, character.getExperience().getLevel());
			stmt.setLong(13, character.getExperience().getExperience());
			stmt.setLong(14, character.getKamas());
			stmt.setInt(15, character.getStats().getEffect(EConstants.ADD_VITALITY.getInt()));
			stmt.setInt(16, character.getStats().getEffect(EConstants.ADD_STRENGTH.getInt()));
			stmt.setInt(17, character.getStats().getEffect(EConstants.ADD_WISDOM.getInt()));
			stmt.setInt(18, character.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt()));
			stmt.setInt(19, character.getStats().getEffect(EConstants.ADD_CHANCE.getInt()));
			stmt.setInt(20, character.getStats().getEffect(EConstants.ADD_AGILITY.getInt()));
			stmt.setInt(21, character.getStatsPoint());
			stmt.setString(22, "");
			stmt.setInt(23, character.getSpellPoint());
			stmt.setInt(24, character.getAlignmentType());
			stmt.setInt(25, character.getAlignmentGrade());
			stmt.setLong(26, character.getAlignment().getExperience());
			stmt.setInt(27, character.getAlignment().getDishonor());
			stmt.setInt(28, character.isShowWings() ? 1 : 0);
			stmt.setInt(29, character.isConnected() ? 1 : 0);
			stmt.setInt(30, character.getId());
			stmt.execute();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			Connector.release(conn);
		}
	}

	public static boolean nicknameIsExist(String nickname) {
		Connection conn = Connector.acquire();
		try {
			PreparedStatement stmt = conn.prepareStatement(
					"SELECT `name` FROM `characters` WHERE `name` = ?");
			stmt.setString(1, nickname);
			try (ResultSet reader = stmt.executeQuery()) {
				if (reader.next())
					return reader.getString("name").equalsIgnoreCase(nickname);
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			logger.error("Impossible to check character name {}: {}", nickname, e.getMessage());
			return true;
		} finally {
			Connector.release(conn);
		}
		return false;
	}

	public static Characters getCharacterById(int id) {
		return characters.get(id);
	}

	private static void ensureSaveColumns(Connection conn) {
		if (saveColumnsReady)
			return;
		try (PreparedStatement ps = conn.prepareStatement(
				"ALTER TABLE `characters` "
						+ "ADD COLUMN IF NOT EXISTS `saveMap` int NOT NULL DEFAULT 0 AFTER `currentCell`, "
						+ "ADD COLUMN IF NOT EXISTS `saveCell` smallint NOT NULL DEFAULT 0 AFTER `saveMap`")) {
			ps.executeUpdate();
			saveColumnsReady = true;
		} catch (SQLException e) {
			logger.debug("CharactersData.ensureSaveColumns ignored: {}", e.getMessage());
		}
	}

	private static int optionalInt(ResultSet rs, String column, int fallback) throws SQLException {
		ResultSetMetaData meta = rs.getMetaData();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			if (column.equalsIgnoreCase(meta.getColumnLabel(i)) || column.equalsIgnoreCase(meta.getColumnName(i))) {
				int value = rs.getInt(column);
				return rs.wasNull() ? fallback : value;
			}
		}
		return fallback;
	}

	public static void removeCharacter(Characters character) {
		characters.remove(character.getId());
	}
}
