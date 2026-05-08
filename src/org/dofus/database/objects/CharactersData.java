package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dofus.constants.EConstants;
import org.dofus.database.Connector;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.characters.Restriction;
import org.dofus.objects.characters.Right;
import org.dofus.objects.experiences.AlignmentExperience;
import org.dofus.objects.experiences.CharacterExperience;
import org.dofus.objects.maps.MapTemplate;

public class CharactersData {

	private static final Connection connection = Connector.getConnection();
	
	//By id
	private static final ConcurrentMap<Integer, Characters> characters = new ConcurrentHashMap<Integer, Characters>();

	public static void load(Account account) {
		try {
			ResultSet reader = connection
            		.createStatement()
            		.executeQuery("SELECT * FROM `characters` WHERE `owner` = '" + account.getId() + "';");
			
			Characters character;
			MapTemplate map;
			ConcurrentMap<Integer, Integer> statistics;
			
			while(reader.next()) {
				map = MapsData.load(reader.getShort("currentMap"));
				
				statistics = new ConcurrentHashMap<Integer, Integer>();
				
				//TODO others and make better
				statistics.put(EConstants.ADD_VITALITY.getInt(), reader.getInt("vitality"));
				statistics.put(EConstants.ADD_STRENGTH.getInt(), reader.getInt("strength"));
				statistics.put(EConstants.ADD_WISDOM.getInt(), reader.getInt("wisdom"));
				statistics.put(EConstants.ADD_INTELLIGENCE.getInt(), reader.getInt("intelligence"));
				statistics.put(EConstants.ADD_CHANCE.getInt(), reader.getInt("chance"));
				statistics.put(EConstants.ADD_AGILITY.getInt(), reader.getInt("agility"));
				
				character = new Characters(
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
						null, //Experience
						reader.getLong("kamas"), 
						statistics, 
						reader.getShort("statsPoint"), 
						reader.getShort("spellPoint"), 
						reader.getByte("alignment"),
						null, //Alignement expS
						(reader.getInt("showWings") == 1)); //Alignment
				
				character.setExperience(new CharacterExperience(
								reader.getShort("level"),
								reader.getLong("experience"),
								ExperiencesData.get(reader.getShort("level")),
								character));
				
				character.setAlignment(new AlignmentExperience(reader.getShort("grade"),
								reader.getLong("honor"),
								reader.getByte("dishonor"),
								ExperiencesData.get(reader.getShort("grade")),
								character));
				
				characters.put(character.getId(), character);
				
				account.addCharacter(character);
				
				System.out.println("Character owner " + character.getOwner().getId() + " loaded with success");
			}
			
		} catch(SQLException e) {
			System.out.println("Impossible to load character : " + e.getMessage());
		}
	}
	
	public static void create(Characters character, int owner) throws SQLException {
		String query = "INSERT INTO `characters`(`id`,`owner`,`name`,`breed`,`gender`,`color1`,`color2`,`color3`," +
                "`skin`,`size`,`currentMap`,`currentCell`,`life`) " +
                "VALUES(?, ?, ?, ?, ?, ?, ? , ?, ?, ?, ?, ?, ?);";
		
		PreparedStatement statement = connection.prepareStatement(query);
		
		statement.setInt(1, character.getId());
		statement.setInt(2, owner);
		statement.setString(3, character.getName());
		statement.setByte(4, character.getBreedId());
		statement.setByte(5, character.getGender());
		statement.setInt(6, character.getColor1());
		statement.setInt(7, character.getColor2());
		statement.setInt(8, character.getColor3());
		statement.setShort(9, character.getSkin());
		statement.setShort(10, character.getSize());
		statement.setInt(11, character.getCurrentMap().getId());
		statement.setShort(12, character.getCurrentCell());
		statement.setInt(13, character.getBreed().getLife());
		
		statement.execute();
		
		MapsData.load(character.getCurrentMap().getId());
		
		WorldData.addCharacterById(character, character.getId());
		
		characters.put(character.getId(), character);
		
		statement.clearParameters();
        statement.close();
	}
	
	public static void delete(Characters character) {
		String query = "DELETE FROM characters WHERE `id` = ?";
		
		PreparedStatement statement;
		try {
			statement = connection.prepareStatement(query);
			statement.setInt(1, character.getId());
			//Items, mounts, guilds, house ?
			
			statement.execute();
			
			removeCharacter(character);
			
			statement.clearParameters();
	        statement.close();
		} catch(SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * FIXME Tous save d'un coup ou juste ce qu'il y a besoin
	 * 	
	 */
	public static void update(Characters character) {
		String query = "UPDATE characters SET "
				+ "`currentMap` = ?,"
				+ "`currentCell` = ?,"
				+ "`currentOrientation` = ?,"
				+ "`rights` = ?,"
				+ "`restrictions` = ?,"
				+ "`channels` = ?,"
				+ "`emotes` = ?,"
				+ "`life` = ?,"
				+ "`energy` = ?,"
				+ "`level` = ?,"
				+ "`experience` = ?,"
				+ "`kamas` = ?,"
				+ "`vitality` = ?,"
				+ "`strength` = ?,"
				+ "`wisdom` = ?,"
				+ "`intelligence` = ?,"
				+ "`chance` = ?,"
				+ "`agility` = ?,"
				+ "`statsPoint` = ?,"
				+ "`spells` = ?,"
				+ "`spellPoint` = ?,"
				+ "`alignment` = ?,"
				+ "`grade` = ?,"
				+ "`honor` = ?,"
				+ "`dishonor` = ?,"
				+ "`showWings` = ?,"
				+ "`connected` = ? "
				
				+ "WHERE `id` = ?;";
		
		PreparedStatement statement;
		try {
			statement = connection.prepareStatement(query);
			
			statement.setInt(1, character.getCurrentMap().getId());
			statement.setInt(2, character.getCurrentCell());
			statement.setInt(3, character.getOrientation().ordinal());
			statement.setInt(4, character.getRight().get());
			statement.setInt(5, character.getRestriction().get());
			statement.setString(6, character.getChannels());
			statement.setString(7, ""); //TODO emote to string
			statement.setInt(8, character.getLife());
			statement.setInt(9, character.getEnergy());
			statement.setInt(10, character.getExperience().getLevel());
			statement.setLong(11, character.getExperience().getExperience());
			statement.setLong(12, character.getKamas());
			statement.setInt(13, character.getStats().getEffect(EConstants.ADD_VITALITY.getInt()));
			statement.setInt(14, character.getStats().getEffect(EConstants.ADD_STRENGTH.getInt()));
			statement.setInt(15, character.getStats().getEffect(EConstants.ADD_WISDOM.getInt()));
			statement.setInt(16, character.getStats().getEffect(EConstants.ADD_INTELLIGENCE.getInt()));
			statement.setInt(17, character.getStats().getEffect(EConstants.ADD_CHANCE.getInt()));
			statement.setInt(18, character.getStats().getEffect(EConstants.ADD_AGILITY.getInt()));
			statement.setInt(19, character.getStatsPoint());
			statement.setString(20, ""); //TODO SPELLS
			statement.setInt(21, character.getSpellPoint());
			statement.setInt(22, character.getAlignmentType());
			statement.setInt(23, character.getAlignmentGrade());
			statement.setLong(24, character.getAlignment().getExperience());
			statement.setInt(25, character.getAlignment().getDishonor());
			statement.setInt(26, character.isShowWings() ? 1 : 0);
			statement.setInt(27, character.isConnected() ? 1 : 0);
			
			statement.setInt(28, character.getId());
			statement.execute();
			
			statement.clearParameters();
	        statement.close();
		} catch(SQLException e) {
			e.printStackTrace();
		}
	}
	public static boolean nicknameIsExist(String nickname) {
		try {
			ResultSet reader = connection
            		.createStatement()
            		.executeQuery("SELECT `name` FROM `characters` WHERE `name` LIKE '" + nickname + "';");
			
			while(reader.next()) {
				if(reader.getString("name").toLowerCase().equals(nickname.toLowerCase()))
					return true;
				else
					return false;
			}
		} catch(SQLException e) {
			System.out.println("Impossible to load accounts " + nickname + " : " + e.getMessage());
			return true;
		}
		return false;
	}
	
	public static Characters getCharacterById(int id) {
		if(!characters.containsKey(id))
			return null;
		return characters.get(id);
	}

	public static void removeCharacter(Characters character) {
		if(characters.containsKey(character.getId()))
			characters.remove(character.getId());
	}
}
