package org.dofus.network.game.handlers.parsers;

import org.apache.mina.core.session.IoSession;
import org.dofus.game.fight.DropTable;
import org.dofus.game.fight.Fight;
import org.dofus.game.fight.Fighter;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.actors.EOrientation;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parseur des packets de combat Dofus 1.29.
 *
 * Packets reçus du client (préfixe 'f') :
 *   fK          — Quitter le combat (en phase de placement seulement)
 *   fN          — Passer son tour (GKK dans le paquet GA)
 *   fP{cell}    — Choisir sa cellule de placement (phase fP)
 *
 * Packets reçus préfixe 'G' (combat) :
 *   GKK / GKE  — Fin de tour (touche fin de tour)
 *   GA{actionId};{unk};{fighterId};{args} — Action de combat
 *
 * Branchement dans {@link org.dofus.network.game.handlers.RolePlayHandler} :
 *   case 'f' → FightParser.parseFightPacket()
 *   GA dans parseGamePacket → si en combat → FightParser.parseAction()
 *
 * TODO : compléter toutes les actions GA (sorts 300+, objets 100+, etc.)
 */
public class FightParser {

    private static final Logger logger = LoggerFactory.getLogger(FightParser.class);

    // ── Entrée principale ─────────────────────────────────────────────────────

    /**
     * Traite un packet combat commençant par 'f'.
     */
    public static void parseFightPacket(Characters character, IoSession session, String packet) {
        if(packet.length() < 2) return;

        switch(packet.charAt(1)) {
            case 'K': // fK — quitter le combat (placement seulement)
                leaveFight(character, session);
                break;
            case 'N': // fN — passer son tour
                passTurn(character, session);
                break;
            case 'P': // fP{cell} — choisir cellule placement
                choosePlacementCell(character, session, packet.substring(2));
                break;
            default:
                logger.debug("FightParser : packet inconnu : {}", packet);
        }
    }

    /**
     * Traite une action GA (mouvement, sort, item...) en combat.
     * Appelé depuis GameParser.action() si le personnage est en combat.
     *
     * Format GA : GA{actionId};{unk};{fighterId};{args}
     */
    public static void parseAction(Characters character, IoSession session, String packet) {
        // ex : "GA1;1;123;aPVa..."   (mouvement)
        //      "GA300;1;123;spellId|targetCell"  (sort)
        if(packet.length() < 3) return;

        String body = packet.substring(2); // retire "GA"
        String[] parts = body.split(";", 4);
        if(parts.length < 3) return;

        int actionId;
        try { actionId = Integer.parseInt(parts[0]); }
        catch(NumberFormatException e) { return; }

        // Retrouver le Fight depuis la session du personnage
        Fight fight = getFightForCharacter(character);
        if(fight == null) {
            logger.debug("FightParser.parseAction : {} n'est pas en combat", character.getName());
            return;
        }

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;

        String args = parts.length >= 4 ? parts[3] : "";
        fight.handleAction(fighter, actionId, args);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static void leaveFight(Characters character, IoSession session) {
        Fight fight = getFightForCharacter(character);
        if(fight == null) return;
        if(fight.getState() != Fight.State.PLACEMENT) {
            session.write("fKE"); // impossible pendant le combat actif
            return;
        }

        // Retire le fighter du combat
        fight.removeFighter(character.getId());
        session.write("fKO");

        // Renvoie le personnage sur la carte (paquet GI = game info reload)
        session.write("GI");

        // Notifie les autres combattants (si d'autres restent)
        fight.broadcast("fK" + character.getId()); // annonce départ

        logger.debug("{} a quitté le combat {} (placement)", character.getName(), fight.getId());
    }

    private static void passTurn(Characters character, IoSession session) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() != Fight.State.ACTIVE) return;

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;

        if(fight.getTurn().getCurrentFighter() != null
                && fight.getTurn().getCurrentFighter().getId() == fighter.getId()) {
            fight.getTurn().endTurn();
        }
    }

    private static void choosePlacementCell(Characters character, IoSession session, String cellStr) {
        Fight fight = getFightForCharacter(character);
        if(fight == null || fight.getState() != Fight.State.PLACEMENT) return;

        short cell;
        try { cell = Short.parseShort(cellStr); }
        catch(NumberFormatException e) { return; }

        Fighter fighter = fight.getFighter(character.getId());
        if(fighter == null) return;

        // TODO : valider que la cellule appartient aux cellules de placement de l'équipe
        fighter.setCell(cell);
        fight.broadcast("fPS" + character.getId() + "|" + cell);
        logger.debug("{} choisit la cellule {} (fight {})", new Object[] { character.getName(), cell, fight.getId()});
    }

    // ── Initiation d'un combat ────────────────────────────────────────────────

    /**
     * Démarre un combat entre un personnage et un groupe de monstres.
     * Appelé quand le joueur clique sur un groupe sur la carte (packet GR ou similaire).
     *
     * @param character  Personnage attaquant
     * @param session    Session du joueur
     * @param groupId    ID du groupe de monstres cible
     */
    public static void initiateFightVsMonsters(Characters character, IoSession session, int groupId) {
        if(character.getCurrentMap() == null) return;

        // Retrouver le groupe sur la carte
        MonsterGroup group = character.getCurrentMap().getMonsterGroups().get(groupId);
        if(group == null) {
            session.write("fJEa"); // groupe non trouvé
            return;
        }

        // Vérif : le joueur n'est pas déjà en combat
        if(getFightForCharacter(character) != null) {
            session.write("fJEb"); // déjà en combat
            return;
        }

        // Retire le groupe de la carte (il réapparaîtra après le combat)
        character.getCurrentMap().removeMonsterGroup(group);
        // Notification de suppression aux joueurs présents
        for(Characters actor : new java.util.ArrayList<>(character.getCurrentMap().getActors().values())) {
            IoSession actorSess = WorldData.getSessionByAccount().get(actor.getOwner());
            if(actorSess != null && actorSess.isConnected())
                actorSess.write(group.toGMRemove());
        }

        // Crée le Fight
        Fight fight = new Fight(character.getCurrentMap());
        fight.setMonsterGroup(group);

        // Fighter joueur (team 0)
        Fighter playerFighter = new Fighter(
            character.getId(), character.getName(),
            Fighter.FighterType.PLAYER, 0,
            character.getLife(), 6, 3,
            character.getStats().getEffect(10), // strength
            character.getStats().getEffect(14), // agility
            character.getStats().getEffect(15), // intel
            character.getStats().getEffect(13), // chance
            character.getStats().getEffect(12), // wisdom
            0, 0, 0, 0, 0, // résistances (TODO items)
            character.getCurrentCell(),
            character.getCurrentOrientation() != null ? character.getCurrentOrientation() : EOrientation.SOUTH
        );
        fight.addFighter(playerFighter);

        // Fighters monstres (team 1)
        for(MonsterGroup.MonsterEntry entry : group.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if(grade == null) continue;
            int monsterId = entry.getTemplate().getId() * 1000 + entry.getGrade(); // ID unique
            Fighter monsterFighter = new Fighter(
                monsterId, entry.getTemplate().getName() + " G" + entry.getGrade(),
                Fighter.FighterType.MONSTER, 1,
                (short) grade.getLife(), grade.getAp(), grade.getMp(),
                grade.getStrength(), grade.getAgility(), grade.getIntel(),
                grade.getChance(), grade.getWisdom(),
                grade.getNeutral(), grade.getEarth(), grade.getFire(),
                grade.getWater(), grade.getAir(),
                group.getCell(),
                EOrientation.SOUTH
            );
            fight.addFighter(monsterFighter);
        }

        // Réponse de join au joueur
        session.write("fJK" + fight.getId()); // join OK

        // Paquet de liste des fighters (fL)
        StringBuilder fL = new StringBuilder("fL");
        for(Fighter f : fight.getFighters()) {
            fL.append(f.toFLEntry()).append(';');
        }
        session.write(fL.toString());

        // Démarrage du combat (sans phase de placement — directement ACTIVE)
        fight.startFight();

        logger.info("Fight {} démarré : {} vs groupe {} ({} monstres)",
        		new Object[] { fight.getId(), character.getName(), groupId, group.getMemberCount()});
    }

    // ── Utilitaire ────────────────────────────────────────────────────────────

    /**
     * Retourne le Fight actif du personnage, ou null s'il n'est pas en combat.
     */
    public static Fight getFightForCharacter(Characters character) {
        for(Fight f : Fight.getActiveFights().values()) {
            if(f.getFighter(character.getId()) != null) return f;
        }
        return null;
    }
}
