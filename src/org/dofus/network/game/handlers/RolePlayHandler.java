package org.dofus.network.game.handlers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.SpellsData;
import org.dofus.network.game.Game;
import org.dofus.network.game.GameClient;
import org.dofus.network.game.GameClientHandler;
import org.dofus.network.game.handlers.parsers.AdminParser;
import org.dofus.network.game.handlers.parsers.BankParser;
import org.dofus.network.game.handlers.parsers.BasicParser;
import org.dofus.network.game.handlers.parsers.BoostParser;
import org.dofus.network.game.handlers.parsers.ChannelParser;
import org.dofus.network.game.handlers.parsers.CraftParser;
import org.dofus.network.game.handlers.parsers.DialogParser;
import org.dofus.network.game.handlers.parsers.ExchangeParser;
import org.dofus.network.game.handlers.parsers.FightParser;
import org.dofus.network.game.handlers.parsers.GameParser;
import org.dofus.network.game.handlers.parsers.GuildParser;
import org.dofus.network.game.handlers.parsers.InventoryParser;
import org.dofus.network.game.handlers.parsers.PartyParser;
import org.dofus.network.game.handlers.parsers.QuestParser;
import org.dofus.network.game.handlers.parsers.SpellParser;
import org.dofus.network.game.handlers.parsers.WaypointParser;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.utils.ChatFilter;
import org.dofus.utils.DeferredSaveService;
import org.dofus.utils.RegenService;
import org.dofus.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RolePlayHandler extends GameClientHandler {
    private static final Logger logger = LoggerFactory.getLogger(RolePlayHandler.class);

    private final IoSession session;
    private final Characters character;

    protected RolePlayHandler(Game game, GameClient client) {
        super(game, client);
        this.session = client.getSession();
        this.character = client.getCharacter();

        session.write("cC+" + character.getChannels());
        session.write("al|");

        SpellsData.loadCharacterSpells(character);
        session.write(SpellsData.buildSLPacket(character.getSpellBook()));
        session.write("AR" + character.getRestriction().toBase36());

        Inventory inv = character.getInventory();
        session.write("Ow" + inv.getUsedPods() + "|" + character.getMaxPods());
        session.write("eL0|");
        session.write("BD" + StringUtils.CURRENT_DATE_FORMATTER.format(new Date()));
        session.write("ZS" + character.getAlignmentType());
        session.write("Im153;127.0.0.1");
        session.write("Im189");

        if (character.getEnergy() <= 2000) session.write("M111|" + character.getEnergy());
        session.write("IC|");
        session.write("BT82800000");
        RegenService.refresh(character, session);

        // Ne pas envoyer l'etat combat ici : le client n'a pas encore charge la map.
        // La reconnexion combat est volontairement rejouee apres GC/GDM puis GI.
    }

    @Override
    public void parse(String packet) throws Exception {
        switch (packet.charAt(0)) {
            case 'A':
                switch (packet.charAt(1)) {
                    case 'B':
                        BoostParser.boost(character, session, Integer.parseInt(packet.substring(2)));
                        break;
                }
                break;
            case 'B':
                parseBasicsPacket(packet);
                break;
            case 'c':
                parseChannelPacket(packet);
                break;
            case 'D':
                parseDialogPacket(packet);
                break;
            case 'E':
                ExchangeParser.parse(character, session, packet);
                break;
            case 'e':
                parseEnvironementPacket(packet);
                break;
            case 'f':
                FightParser.parseFightPacket(character, session, packet);
                break;
            case 'G':
                parseGamePacket(packet);
                break;
            case 'g':
                GuildParser.parse(character, session, packet);
                break;
            case 'O':
                InventoryParser.parse(character, session, client, packet);
                break;
            case 'M':
                CraftParser.parse(character, session, packet);
                break;
            case 'P':
                parsePartyPacket(packet);
                break;
            case 'Q':
                QuestParser.parse(character, session, packet);
                break;
            case 'S':
                SpellParser.parse(character, session, packet);
                break;
            case 'W':
                parseWaypointPacket(packet);
                break;
        }
    }

    private void parsePartyPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'A': PartyParser.accept(character, session); break;
            case 'I': PartyParser.invitation(character, session, packet.substring(2)); break;
            case 'R': PartyParser.refuse(character, session); break;
            case 'V': PartyParser.leave(character, session, packet); break;
        }
    }

    private void parseWaypointPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'u':
            case 'U':
                WaypointParser.use(character, session, client, packet);
                break;
            case 'v':
                WaypointParser.panelZaapis(character, session);
                break;
            case 'V':
                WaypointParser.panelZaaps(character, session);
                break;
            default:
                break;
        }
    }

    private void parseDialogPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'C': DialogParser.create(character, session, packet); break;
            case 'R': DialogParser.reply(character, session, packet); break;
            case 'V': DialogParser.quit(character, session); break;
        }
    }

    private void parseBasicsPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'a':
                if (packet.length() > 2 && packet.charAt(2) == 'M') {
                    BasicParser.moveByClickMap(character, session, client, packet);
                }
                break;
            case 'A':
                AdminParser.parseBasicAdmin(character, session, client, packet);
                break;
            case 'd':
            case 'k':
            case 'q':
            case 'i':
            case 'o':
                BankParser.parse(character, session, packet);
                break;
            case 'M':
                BasicParser.channelsMessage(character, session, client, packet);
                break;
            case 'S':
                BasicParser.emoticons(character, packet);
                break;
            case 'Y':
                BasicParser.states(packet);
                break;
            default:
                break;
        }
    }

    private void parseEnvironementPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'D':
                if (packet.length() < 3) break;
                try {
                    int orientOrd = Integer.parseInt(packet.substring(2));
                    org.dofus.objects.actors.EOrientation orientation = org.dofus.objects.actors.EOrientation.valueOf(orientOrd);
                    if (orientation == null) break;
                    character.setCurrentOrientation(orientation);
                    String broadcast = "eD" + character.getId() + "|" + orientOrd;
                    for (Characters actor : character.getCurrentMap().getActors().values()) {
                        IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
                        if (actorSession != null && actorSession.isConnected()) actorSession.write(broadcast);
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
                break;
        }
    }

    private void parseChannelPacket(String packet) {
        switch (packet.charAt(1)) {
            case 'C':
                ChannelParser.change(character, packet);
                break;
        }
    }

    private void parseGamePacket(String packet) throws Exception {
        switch (packet.charAt(1)) {
            case 'A':
                if (FightParser.getFightForCharacter(character) != null) {
                    FightParser.parseAction(character, session, packet);
                } else {
                    GameParser.action(session, client, packet);
                }
                break;
            case 'C':
                // En reconnexion combat, on force d'abord le chargement map normal (GCK/GDM).
                // L'etat combat sera envoye seulement apres le GI du client.
                GameParser.creation(character, session);
                break;
            case 'I':
                if (FightParser.getFightForCharacter(character) != null) {
                    FightParser.reconnectIfNeeded(character, session);
                } else {
                    GameParser.information(character, session, client, character.getCurrentMap());
                }
                break;
            case 'K':
                if (FightParser.getFightForCharacter(character) != null) {
                    // GKK en combat confirme juste la fin d'une animation client.
                    // Ne pas le router vers la pile d'actions roleplay.
                    break;
                }
                if (packet.length() > 2) GameParser.endAction(client, packet.charAt(2) == 'K', packet.substring(3));
                break;
            case 'p':
                if (FightParser.getFightForCharacter(character) != null && packet.length() > 2) {
                    FightParser.choosePlacementCell(character, session, packet.substring(2));
                }
                break;
            case 'R':
                if (packet.length() > 2) {
                    if (FightParser.getFightForCharacter(character) != null && packet.length() == 3 && (packet.charAt(2) == '0' || packet.charAt(2) == '1')) {
                        FightParser.setReady(character, session, packet.charAt(2) == '1');
                        break;
                    }
                    try {
                        int groupId = Integer.parseInt(packet.substring(2));
                        GameParser.attackMonsterGroup(session, client, groupId);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                break;
            case 'Q':
                if (FightParser.leaveSpectator(character, session)) break;
                if (FightParser.getFightForCharacter(character) != null) {
                    FightParser.parseFightPacket(character, session, "fK");
                } else {
                    session.write("BN");
                }
                break;
            case 'S':
                if (FightParser.getFightForCharacter(character) != null) {
                    FightParser.parseFightPacket(character, session, "fN");
                }
                break;
            case 't':
                if (FightParser.getFightForCharacter(character) != null) {
                    FightParser.passTurn(character, session);
                }
                break;
        }
    }

    @Override
    public void onClosed() {
        FightParser.removeSpectatorSession(session);

        org.dofus.game.fight.Fight activeFight = FightParser.getFightForCharacter(character);
        if (activeFight != null && activeFight.getState() != org.dofus.game.fight.Fight.State.FINISHED) {
            FightParser.markDisconnected(character);
            DeferredSaveService.cancel(character.getId());
            CharactersData.update(character);
            WorldData.removeSessionByAccount(client.getAccount());
            WorldData.removeController(character.getId());
            character.setConnected(false);
            client.getAccount().setConnected(false);
            ChatFilter.remove(character.getId());
            BankParser.evictCache(client.getAccount().getId());
            RegenService.stop(character);
            logger.debug("RolePlayHandler closed for character {} during fight {}", character.getName(), activeFight.getId());
            return;
        }

        character.getCurrentMap().removeActor(character);
        List<Characters> snapshot = new ArrayList<Characters>(character.getCurrentMap().getActors().values());
        for (Characters actor : snapshot) {
            IoSession actorSession = WorldData.getSessionByAccount().get(actor.getOwner());
            if (actorSession != null && actorSession.isConnected() && !actorSession.equals(client.getSession())) {
                actorSession.write("GM|-" + character.getId());
            }
        }

        DeferredSaveService.cancel(character.getId());
        CharactersData.update(character);
        WorldData.removeCharacterById(character.getId());
        WorldData.removeCharacterByName(character.getName());
        WorldData.removeSessionByAccount(client.getAccount());
        WorldData.removeController(character.getId());
        CharactersData.removeCharacter(character);
        character.setDialogNpc(null);
        character.setConnected(false);
        client.getAccount().setConnected(false);
        ChatFilter.remove(character.getId());
        BankParser.evictCache(client.getAccount().getId());
        RegenService.stop(character);
        logger.debug("RolePlayHandler closed for character {}", character.getName());
    }
}
