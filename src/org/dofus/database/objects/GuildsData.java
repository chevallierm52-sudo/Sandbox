package org.dofus.database.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.dofus.database.Connector;
import org.dofus.objects.guilds.Guild;
import org.dofus.objects.guilds.GuildMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistance des guildes Dofus 1.29.
 *
 * Tables attendues :
 *   guilds  (id, name, emblem, level, experience)
 *   guild_members (guild_id, character_id, character_name, rank, rights, experience, level, breed, gender)
 *
 * TODO : créer guild_system.sql
 */
public class GuildsData {

    private static final Logger logger = LoggerFactory.getLogger(GuildsData.class);

    private static final ConcurrentMap<Integer, Guild>  guilds       = new ConcurrentHashMap<>();
    /** characterId → guildId (cache rapide pour lookup au login) */
    private static final ConcurrentMap<Integer, Integer> memberIndex = new ConcurrentHashMap<>();
    private static final AtomicInteger ID_GEN = new AtomicInteger(1);

    // ── Chargement ────────────────────────────────────────────────────────────

    public static void load() {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            guilds.clear();
            memberIndex.clear();

            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name, emblem, level, experience FROM guilds");
                ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    Guild g = new Guild(
                        rs.getInt("id"), rs.getString("name"),
                        rs.getString("emblem"), rs.getInt("level"), rs.getLong("experience")
                    );
                    guilds.put(g.getId(), g);
                    if(g.getId() >= ID_GEN.get()) ID_GEN.set(g.getId() + 1);
                }
            }

            try(PreparedStatement ps = conn.prepareStatement(
                    "SELECT guild_id, character_id, character_name, rank, rights, " +
                    "experience, level, breed, gender FROM guild_members");
                ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    Guild guild = guilds.get(rs.getInt("guild_id"));
                    if(guild == null) continue;
                    GuildMember m = new GuildMember(
                        rs.getInt("character_id"), rs.getString("character_name"),
                        rs.getInt("rank"), rs.getInt("rights"), rs.getLong("experience"),
                        rs.getInt("level"), rs.getInt("breed"), rs.getByte("gender")
                    );
                    guild.addMember(m);
                    memberIndex.put(m.getCharacterId(), guild.getId());
                }
            }
            logger.info("GuildsData : {} guildes chargées", guilds.size());
        } catch(Exception e) {
            logger.error("GuildsData.load() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    // ── Création / Suppression ─────────────────────────────────────────────────

    public static Guild create(String name, String emblem, GuildMember founder) {
        int id = ID_GEN.getAndIncrement();
        Guild guild = new Guild(id, name, emblem, 1, 0);
        guild.addMember(founder);
        guilds.put(id, guild);
        memberIndex.put(founder.getCharacterId(), id);

        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO guilds (id, name, emblem, level, experience) VALUES (?, ?, ?, 1, 0)")) {
                ps.setInt(1, id); ps.setString(2, name); ps.setString(3, emblem);
                ps.executeUpdate();
            }
            insertMember(conn, id, founder);
        } catch(Exception e) {
            logger.error("GuildsData.create() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
        return guild;
    }

    public static void addMember(Guild guild, GuildMember member) {
        guild.addMember(member);
        memberIndex.put(member.getCharacterId(), guild.getId());
        Connection conn = null;
        try {
            conn = Connector.acquire();
            insertMember(conn, guild.getId(), member);
        } catch(Exception e) {
            logger.error("GuildsData.addMember() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static void removeMember(Guild guild, int characterId) {
        guild.removeMember(characterId);
        memberIndex.remove(characterId);
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM guild_members WHERE guild_id=? AND character_id=?")) {
                ps.setInt(1, guild.getId()); ps.setInt(2, characterId);
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("GuildsData.removeMember() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }


    public static void updateMember(Guild guild, GuildMember member) {
        if(guild == null || member == null) return;
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "UPDATE guild_members SET level=?, breed=?, gender=? WHERE guild_id=? AND character_id=?")) {
                ps.setInt(1, member.getLevel());
                ps.setInt(2, member.getBreed());
                ps.setByte(3, member.getGender());
                ps.setInt(4, guild.getId());
                ps.setInt(5, member.getCharacterId());
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("GuildsData.updateMember() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    public static void save(Guild guild) {
        Connection conn = null;
        try {
            conn = Connector.acquire();
            try(PreparedStatement ps = conn.prepareStatement(
                    "UPDATE guilds SET name=?, emblem=?, level=?, experience=? WHERE id=?")) {
                ps.setString(1, guild.getName()); ps.setString(2, guild.getEmblem());
                ps.setInt(3, guild.getLevel());   ps.setLong(4, guild.getExperience());
                ps.setInt(5, guild.getId());
                ps.executeUpdate();
            }
        } catch(Exception e) {
            logger.error("GuildsData.save() failed: {}", e.getMessage());
        } finally {
            if(conn != null) Connector.release(conn);
        }
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    public static Guild  getById(int id)          { return guilds.get(id);         }
    public static Guild  getByMember(int charId)  {
        Integer gid = memberIndex.get(charId);
        return gid != null ? guilds.get(gid) : null;
    }
    public static ConcurrentMap<Integer, Guild> getAll() { return guilds; }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static void insertMember(Connection conn, int guildId, GuildMember m) throws Exception {
        try(PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO guild_members (guild_id, character_id, character_name, rank, rights, experience, level, breed, gender) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, guildId); ps.setInt(2, m.getCharacterId());
            ps.setString(3, m.getCharacterName()); ps.setInt(4, m.getRank());
            ps.setInt(5, m.getRights()); ps.setLong(6, m.getExperience());
            ps.setInt(7, m.getLevel()); ps.setInt(8, m.getBreed());
            ps.setByte(9, m.getGender());
            ps.executeUpdate();
        }
    }
}
