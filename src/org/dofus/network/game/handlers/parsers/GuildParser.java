package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.GuildsData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.guilds.Guild;
import org.dofus.objects.guilds.GuildMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur des packets de guilde Dofus 1.29 (préfixe 'g').
 *
 * Packets reçus du client :
 *   gC{name}|{emblem}   — créer une guilde
 *   gD                  — dissoudre la guilde
 *   gI                  — demander les infos guilde
 *   gJ{characterId}     — accepter une invitation
 *   gK{characterId}     — exclure un membre
 *   gL                  — liste des membres
 *   gM{msg}             — message guilde (canal $)
 *   gP{mapId}|{cell}    — placer un percepteur
 *
 * Branchement dans {@link org.dofus.network.game.handlers.RolePlayHandler} :
 *   Décommenter {@code case 'g': parseGuildPacket(packet); break;}
 *
 * TODO : implémenter complètement après le système d'inventaire (les guildes
 *        nécessitent des objets pour les percepteurs et le coffre).
 */
public class GuildParser {

    private static final Logger logger = LoggerFactory.getLogger(GuildParser.class);

    public static void parse(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;
        switch(packet.charAt(1)) {
            case 'C': createGuild(character, session, packet.substring(2)); break;
            case 'D': dissolveGuild(character, session);                    break;
            case 'I': guildInfo(character, session);                        break;
            case 'J': acceptInvitation(character, session, packet.substring(2)); break;
            case 'K': kickMember(character, session, packet.substring(2));  break;
            case 'L': memberList(character, session);                       break;
            default:  logger.debug("GuildParser : packet inconnu : {}", packet);
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static void createGuild(Characters character, IoSession session, String args) {
        if(GuildsData.getByMember(character.getId()) != null) {
            session.write("gCEa"); // déjà dans une guilde
            return;
        }
        String[] parts = args.split("\\|", 2);
        if(parts.length < 2) { session.write("gCEa"); return; }

        String name   = parts[0].trim();
        String emblem = parts[1].trim();

        if(name.isEmpty() || name.length() > 30) { session.write("gCEn"); return; }

        // TODO : vérifier que le nom n'est pas déjà pris (GuildsData.findByName)

        GuildMember founder = new GuildMember(
            character.getId(), character.getName(),
            10, 0xFF, // rang 10 = meneur, tous les droits
            0, character.getExperience().getLevel(),
            character.getBreed().getId(), character.getGender()
        );
        Guild guild = GuildsData.create(name, emblem, founder);

        session.write("gCK" + guild.getId()); // création OK
        session.write(guild.toGIPacket());
        logger.info("{} a créé la guilde '{}'", character.getName(), name);
    }

    private static void dissolveGuild(Characters character, IoSession session) {
        Guild guild = GuildsData.getByMember(character.getId());
        if(guild == null) { session.write("gDE"); return; }
        GuildMember m = guild.getMember(character.getId());
        if(m == null || m.getRank() < 10) { session.write("gDE"); return; } // pas meneur

        // Notifie tous les membres connectés
        for(GuildMember member : guild.getMembers()) {
            Characters memberChar = WorldData.getCharacterById(member.getCharacterId());
            if(memberChar == null) continue;
            IoSession s = WorldData.getSessionByAccount().get(memberChar.getOwner());
            if(s != null && s.isConnected()) s.write("gDK");
        }
        // TODO : GuildsData.delete(guild)
        logger.info("{} a dissous la guilde '{}'", character.getName(), guild.getName());
    }

    private static void guildInfo(Characters character, IoSession session) {
        Guild guild = GuildsData.getByMember(character.getId());
        if(guild == null) { session.write("gIE"); return; }
        session.write(guild.toGIPacket());
    }

    private static void acceptInvitation(Characters character, IoSession session, String inviterIdStr) {
        // TODO : système d'invitation (stocker l'invitation comme pour les groupes)
        session.write("BN"); // placeholder
    }

    private static void kickMember(Characters character, IoSession session, String targetIdStr) {
        Guild guild = GuildsData.getByMember(character.getId());
        if(guild == null) return;
        GuildMember requester = guild.getMember(character.getId());
        if(requester == null || !requester.canManageMembers()) { session.write("BN"); return; }

        try {
            int targetId = Integer.parseInt(targetIdStr);
            GuildMember target = guild.getMember(targetId);
            if(target == null || target.getRank() >= requester.getRank()) return;

            GuildsData.removeMember(guild, targetId);

            // Notifie la cible si connectée
            Characters targetChar = WorldData.getCharacterById(targetId);
            if(targetChar != null) {
                IoSession ts = WorldData.getSessionByAccount().get(targetChar.getOwner());
                if(ts != null && ts.isConnected()) ts.write("gKK");
            }
            session.write("gKK");
        } catch(NumberFormatException e) { /* ignore */ }
    }

    private static void memberList(Characters character, IoSession session) {
        Guild guild = GuildsData.getByMember(character.getId());
        if(guild == null) return;
        session.write(guild.toGLPacket());
    }
}
