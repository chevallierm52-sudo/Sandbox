package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.game.fight.Fight;
import org.dofus.game.fight.Fighter;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FightParser {

    private static final Logger logger = LoggerFactory.getLogger(FightParser.class);

    public static void parseFightPacket(Characters character, IoSession session, String packet) {
        if(packet == null || packet.length() < 2) return;

        switch(packet.charAt(1)) {
            case 'K':
                leaveFight(character, session);
                break;
            case 'N':
                passTurn(character, session);
                break;
            case 'P':
                choosePlacementCell(character, session, packet.substring(2));
                break;
            case 'L':
                sendFightsList(character, session);
                break;
            case 'D':
                sendFightDetails(session, packet.substring(2));
                break;
            case 'V':
                leaveSpectator(character, session);
                break;
            case 'S':
                session.write("BN");
                break;
            default:
                logger.debug("FightParser unknown packet: {}", packet);
        }
    }

    public static void parseAction(Characters character, IoSession session, String packet) {
        if(packet == null || packet.length() < 5) return;

        Fight fight = getFightForCharacter(character);
        if(fight == null) return;

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;

        Integer actionId = parseOfficialActionId(packet);
        if(actionId != null) {
            String args = packet.substring(5);
            if(actionId == 300) {
                String[] spellArgs = args.split(";", 2);
                if(spellArgs.length < 2) return;
                try {
                    int spellId = Integer.parseInt(spellArgs[0]);
                    fight.handleAction(fighter, 300 + spellId, spellArgs[1]);
                } catch(NumberFormatException ignored) {
                    return;
                }
            } else {
                fight.handleAction(fighter, actionId, args);
            }
            return;
        }

        parseLegacyAction(fight, fighter, packet);
    }

    private static Integer parseOfficialActionId(String packet) {
        try {
            return Integer.valueOf(Integer.parseInt(packet.substring(2, 5)));
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private static void parseLegacyAction(Fight fight, Fighter fighter, String packet) {
        String body = packet.substring(2);
        String[] parts = body.split(";", 4);
        if(parts.length < 3) return;

        int actionId;
        try { actionId = Integer.parseInt(parts[0]); }
        catch(NumberFormatException e) { return; }

        String args = parts.length >= 4 ? parts[3] : "";
        if(actionId == 300) {
            String[] spellArgs = args.split("[|;]", 2);
            if(spellArgs.length < 2) return;
            try {
                int spellId = Integer.parseInt(spellArgs[0]);
                fight.handleAction(fighter, 300 + spellId, spellArgs[1]);
            } catch(NumberFormatException ignored) {
                return;
            }
        } else {
            fight.handleAction(fighter, actionId, args);
        }
    }

    public static void setReady(Characters character, IoSession session, boolean ready) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() != Fight.State.PLACEMENT) return;
        Fighter fighter = fight.getFighter(character.getId());
        if(fighter != null) fight.setReady(fighter, ready);
    }

    public static void choosePlacementCell(Characters character, IoSession session, String cellStr) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() != Fight.State.PLACEMENT) return;

        short cell;
        try { cell = Short.parseShort(cellStr); }
        catch(NumberFormatException e) { return; }

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;
        if(!fight.choosePlacementCell(fighter, cell)) session.write("GICe");
    }

    public static void passTurn(Characters character, IoSession session) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() != Fight.State.ACTIVE) return;

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;

        if(fight.getTurn().getCurrentFighter() != null
                && fight.getTurn().getCurrentFighter().getId() == fighter.getId()) {
            fight.getTurn().endTurn();
        }
    }

    private static void leaveFight(Characters character, IoSession session) {
        Fight fight = getFightForCharacter(character);
        if(fight == null) return;

        if(fight.getState() == Fight.State.PLACEMENT) {
            // Abandon en phase placement : pas de panneau résultat (AncestraR n'en envoie pas
            // non plus dans ce cas — pas de gain/perte tant que le combat n'a pas démarré).
            // GV ferme la fenêtre combat, le joueur revient sur la map.
            fight.removeFighter(character.getId());
            session.write("GV");
            return;
        }

        if(fight.getState() == Fight.State.ACTIVE) {
            // Abandon en combat actif : handleAbandon marque mort + déclenche endFight()
            // si plus aucun fighter de team0 vivant → endFight broadcaste GE puis GV à tous.
            // Le panneau résultat s'affiche côté client.
            Fighter fighter = fight.getFighter(character.getId());
            if(fighter != null) fight.handleAbandon(fighter);
            return;
        }

        session.write("BN");
    }

    public static boolean leaveSpectator(Characters character, IoSession session) {
        Fight fight = getSpectatedFight(session);
        if(fight == null) return false;

        if(fight.removeSpectator(session)) {
            session.write("GV");
            logger.debug("Fight {} spectator left: {}", fight.getId(), character != null ? character.getName() : "?");
            return true;
        }
        return false;
    }

    public static void removeSpectatorSession(IoSession session) {
        Fight fight = getSpectatedFight(session);
        if(fight != null) fight.removeSpectator(session);
    }

    public static void initiateFightVsMonsters(Characters character, IoSession session, int targetIdOrCell) {
        if(character == null || character.getCurrentMap() == null) return;
        MapTemplate map = character.getCurrentMap();

        MonsterGroup group = findMonsterGroupByIdOrCell(map, targetIdOrCell);
        if(group == null) {
            session.write("BN");
            return;
        }

        if(getFightForCharacter(character) != null) {
            session.write("BN");
            return;
        }

        map.removeMonsterGroup(group);
        broadcastOnMap(map, group.toGMRemove(), null);

        map.removeActor(character);
        broadcastOnMap(map, "GM|-" + character.getId(), character);
        org.dofus.utils.RegenService.stop(character);

        Fight fight = new Fight(map);
        fight.setMonsterGroup(group);

        Fighter playerFighter = buildPlayerFighter(character);
        fight.addFighter(playerFighter);

        // Les fighter IDs mob doivent être NÉGATIFS pour que le client 1.29 les reconnaisse
        // comme mobs (AncestraR Monstre.MobGroup utilise guid = -1, -2, -3...
        // StarLoco Fight.java:200 idem via entry.getKey() qui est négatif descendant).
        int monsterIndex = 0;
        int monsterFightId = -1;
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if(grade == null) continue;
            Fighter monster = buildMonsterFighter(group, entry, grade, monsterIndex++, monsterFightId--);
            fight.addFighter(monster);
        }

        fight.ensurePlacementFallbacks(character.getCurrentCell(), group.getCell());
        placeInitialFighters(fight, character.getCurrentCell(), group.getCell());
        fight.startPlacement();

        logger.info("Fight {} placement started: {} vs group {} on map {}",
                new Object[] { fight.getId(), character.getName(), group.getId(), map.getId() });
    }

    private static Fighter buildPlayerFighter(Characters character) {
        short life = (short)Math.max(1, Math.min(character.getLife(), character.getLifeMax()));
        Fighter fighter = new Fighter(
                character.getId(),
                character.getName(),
                Fighter.FighterType.PLAYER,
                0,
                life,
                Statistic.totalWithEquipment(character, EConstants.ADD_AP.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_MP.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_STRENGTH.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_AGILITY.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_INTELLIGENCE.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_CHANCE.getInt()),
                Statistic.totalWithEquipment(character, EConstants.ADD_WISDOM.getInt()),
                Statistic.totalWithEquipment(character, EConstants.RESIST_PERCENT_NEUTRAL.getInt()),
                Statistic.totalWithEquipment(character, EConstants.RESIST_PERCENT_EARTH.getInt()),
                Statistic.totalWithEquipment(character, EConstants.RESIST_PERCENT_FIRE.getInt()),
                Statistic.totalWithEquipment(character, EConstants.RESIST_PERCENT_WATER.getInt()),
                Statistic.totalWithEquipment(character, EConstants.RESIST_PERCENT_AIR.getInt()),
                character.getCurrentCell(),
                character.getCurrentOrientation() != null ? character.getCurrentOrientation() : EOrientation.SOUTH);
        fighter.setInitiative(Statistic.totalWithEquipment(character, EConstants.ADD_INITIATIVE.getInt()) + life);
        fighter.setVisual(character.getExperience().getLevel(), character.getSkin());
        fighter.setMaxLife((short)Math.max(1, character.getLifeMax()));
        return fighter;
    }

    private static Fighter buildMonsterFighter(MonsterGroup group, MonsterGroup.MonsterEntry entry,
            MonsterTemplate.MonsterGrade grade, int index, int fightId) {
        // fightId est NÉGATIF (-1, -2, -3...) selon convention StarLoco/AncestraR.
        // Le client Dofus 1.29 attend des IDs négatifs pour distinguer mob d'un joueur en combat.
        Fighter fighter = new Fighter(
                fightId,
                entry.getTemplate().getName(),
                Fighter.FighterType.MONSTER,
                1,
                (short)grade.getLife(),
                grade.getAp(),
                grade.getMp(),
                grade.getStrength(),
                grade.getAgility(),
                grade.getIntel(),
                grade.getChance(),
                grade.getWisdom(),
                grade.getNeutral(),
                grade.getEarth(),
                grade.getFire(),
                grade.getWater(),
                grade.getAir(),
                group.getCell(),
                EOrientation.SOUTH);
        fighter.setVisual(grade.getLevel(), entry.getTemplate().getGfxId());
        fighter.setTemplateId(entry.getTemplate().getId());
        fighter.setMobGrade(entry.getGrade());
        fighter.setInitiative(grade.getAgility() + grade.getWisdom() / 10 + grade.getLevel());
        fighter.setReady(true);
        return fighter;
    }

    private static void placeInitialFighters(Fight fight, short playerOrigin, short monsterOrigin) {
        for(Fighter f : fight.getTeam(0)) {
            f.setCell(fight.pickPlacementCell(f, playerOrigin));
            f.setReady(false);
        }
        for(Fighter f : fight.getTeam(1)) {
            f.setCell(fight.pickPlacementCell(f, monsterOrigin));
            f.setReady(true);
        }
    }

    private static MonsterGroup findMonsterGroupByIdOrCell(MapTemplate map, int targetIdOrCell) {
        MonsterGroup group = map.getMonsterGroups().get(targetIdOrCell);
        if(group != null) return group;
        for(MonsterGroup candidate : map.getMonsterGroups().values()) {
            if(candidate != null && candidate.getCell() == targetIdOrCell) return candidate;
        }
        return null;
    }

    public static void joinFightAsSpectator(Characters character, IoSession session, int fightId) {
        Fight fight = Fight.getFight(fightId);
        if(fight == null || fight.getState() == Fight.State.FINISHED) {
            session.write("BN");
            return;
        }
        fight.addSpectator(character, session);
    }

    public static void sendFightsList(Characters character, IoSession session) {
        StringBuilder sb = new StringBuilder("fL");
        if(character == null || character.getCurrentMap() == null) {
            session.write(sb.toString());
            return;
        }
        for(Fight fight : Fight.getActiveFights().values()) {
            if(fight.getMap() != character.getCurrentMap()) continue;
            sb.append('|').append(fight.buildFightListEntry());
        }
        session.write(sb.toString());
    }

    public static int countFightsOnMap(MapTemplate map) {
        if(map == null) return 0;
        int count = 0;
        for(Fight fight : Fight.getActiveFights().values()) {
            if(fight.getMap() == map && fight.getState() != Fight.State.FINISHED) count++;
        }
        return count;
    }

    private static void sendFightDetails(IoSession session, String idStr) {
        try {
            Fight fight = Fight.getFight(Integer.parseInt(idStr));
            if(fight == null) {
                session.write("BN");
                return;
            }
            session.write(fight.buildFightDetailsPacket());
        } catch(NumberFormatException e) {
            session.write("BN");
        }
    }

    public static void markDisconnected(Characters character) {
        Fight fight = getFightForCharacter(character);
        if(fight != null) fight.handleDisconnect(character.getId());
    }

    public static boolean reconnectIfNeeded(Characters character, IoSession session) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() == Fight.State.FINISHED) return false;
        fight.reconnect(character, session);
        return true;
    }

    public static Fight getFightForCharacter(Characters character) {
        if(character == null) return null;
        for(Fight f : Fight.getActiveFights().values()) {
            if(f.getFighter(character.getId()) != null) return f;
        }
        return null;
    }

    public static Fight getSpectatedFight(IoSession session) {
        if(session == null) return null;
        for(Fight fight : Fight.getActiveFights().values()) {
            if(fight.isSpectator(session)) return fight;
        }
        return null;
    }

    private static void broadcastOnMap(MapTemplate map, String packet, Characters except) {
        for(Characters actor : new java.util.ArrayList<Characters>(map.getActors().values())) {
            if(actor == null || actor == except) continue;
            IoSession actorSess = WorldData.getSessionByAccount().get(actor.getOwner());
            if(actorSess != null && actorSess.isConnected()) actorSess.write(packet);
        }
    }
}
