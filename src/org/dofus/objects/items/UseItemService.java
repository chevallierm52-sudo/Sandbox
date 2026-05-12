package org.dofus.objects.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.ItemsData;
import org.dofus.database.objects.MapsData;
import org.dofus.database.objects.UseItemActionsData;
import org.dofus.game.actions.RolePlayMovement;
import org.dofus.network.game.GameClient;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.utils.RegenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution des actions de use_item_actions.
 * Important : le type 5 est traite en dernier, comme consommation de l'objet.
 */
public final class UseItemService {

    private static final Logger logger = LoggerFactory.getLogger(UseItemService.class);
    private static final Random RANDOM = new Random();

    private UseItemService() {}

    public static boolean use(Characters character, IoSession session, GameClient client, Item item) {
        if(character == null || session == null || item == null || item.getTemplate() == null) return false;

        List<UseItemAction> actions = UseItemActionsData.getActions(item.getTemplate().getId());
        if(actions.isEmpty()) actions = buildDerivedActions(item);
        if(actions.isEmpty()) return false;

        boolean consumed = false;
        boolean changedStats = false;
        boolean changedInventory = false;
        boolean executed = false;

        for(UseItemAction action : actions) {
            if(action.getType() == 5) continue;
            ActionResult result = executeAction(character, session, client, item, action);
            if(result.executed) executed = true;
            if(result.changedStats) changedStats = true;
            if(result.changedInventory) changedInventory = true;
        }

        for(UseItemAction action : actions) {
            if(action.getType() != 5) continue;
            if(consumeOne(character, session, item)) {
                consumed = true;
                changedInventory = true;
            }
            break;
        }

        if(changedStats) {
            CharactersData.update(character);
            session.write(Statistic.getStatisticsMessage(character));
        }
        if(changedInventory || consumed) {
            session.write("Ow" + character.getInventory().getUsedPods() + "|" + character.getMaxPods());
        }

        if(executed || consumed) return true;
        session.write("BN");
        return true;
    }

    private static List<UseItemAction> buildDerivedActions(Item item) {
        List<UseItemAction> derived = new ArrayList<UseItemAction>();
        if(item == null || item.getTemplate() == null) return derived;

        int typeId = item.getTemplate().getTypeId();
        boolean usableType = typeId == 69 || typeId == 74 || typeId == 89;
        if(!usableType) return derived;

        for(ItemEffect effect : item.getRolledEffects()) {
            int effectId = effect.getEffectId();
            int value = Math.max(0, effect.getValue());
            if(value <= 0) continue;

            // Potions de soin stockées uniquement comme effet client 6e/110.
            if(effectId == EConstants.ADD_LIFE.getInt()) {
                derived.add(new UseItemAction(item.getTemplate().getId(), 10, value + "," + value));
            }
        }

        if(!derived.isEmpty()) derived.add(new UseItemAction(item.getTemplate().getId(), 5, ""));
        return derived;
    }

    private static ActionResult executeAction(Characters character, IoSession session, GameClient client, Item item, UseItemAction action) {
        try {
            switch(action.getType()) {
                case 0:  return teleport(character, session, client, action);          // args: map,cell
                case 8:  return addPermanentStat(character, session, action);          // args: effectId,value
                case 9:  return learnSpellPlaceholder(session, action);                // args: spellId
                case 10: return heal(character, session, action);                      // args: min,max
                case 21: return addEnergy(character, session, action);                 // args: value
                case 28: return learnEmotePlaceholder(session, action);                // args: emoteId
                case 228:return launchVisualEffect228(character, action);              // args: effectId client, ex fées d'artifice
                default:
                    logger.debug("UseItemService : action non implementee {}", action);
                    return ActionResult.none();
            }
        } catch(Exception e) {
            logger.warn("UseItemService : echec action {} : {}", action, e.getMessage());
            session.write("Im11");
            return ActionResult.none();
        }
    }

    private static ActionResult teleport(Characters character, IoSession session, GameClient client, UseItemAction action) {
        String[] args = action.splitArgs();
        if(args.length < 2 || client == null) return ActionResult.none();

        int mapId = parseInt(args[0], -1);
        short cellId = (short) parseInt(args[1], -1);
        if(mapId <= 0 || cellId < 0) return ActionResult.none();

        MapTemplate map = MapsData.load(mapId);
        if(map == null) {
            session.write("Im11");
            return ActionResult.none();
        }

        short safeCell = cellId;
        if(!map.isValidActorCell(safeCell, true)) {
            Short nearest = map.findNearestValidActorCell(safeCell, true);
            if(nearest == null) {
                session.write("Im11");
                return ActionResult.none();
            }
            safeCell = nearest.shortValue();
        }

        RolePlayMovement.teleport(client, map, safeCell);
        CharactersData.update(character);
        return ActionResult.executed();
    }

    private static ActionResult heal(Characters character, IoSession session, UseItemAction action) {
        String[] args = action.splitArgs();
        if(args.length < 2) return ActionResult.none();

        int min = parseInt(args[0], 0);
        int max = parseInt(args[1], min);
        if(max < min) max = min;
        int heal = min + (max > min ? RANDOM.nextInt(max - min + 1) : 0);
        if(heal <= 0) return ActionResult.none();

        short lifeMax = character.getLifeMax();
        if(character.getLife() >= lifeMax) {
            // L'officiel consomme souvent quand meme certains consommables.
            // Ici on evite juste le crash et on laisse le type 5 retirer l'item si present.
            return ActionResult.executed();
        }

        int nextLife = Math.min(lifeMax, character.getLife() + heal);
        character.setLife((short) nextLife);
        CharactersData.update(character);
        RegenService.refresh(character, session);
        session.write(Statistic.getStatisticsMessage(character));
        return ActionResult.stats();
    }

    private static ActionResult addEnergy(Characters character, IoSession session, UseItemAction action) {
        String[] args = action.splitArgs();
        if(args.length < 1) return ActionResult.none();

        int value = parseInt(args[0], 0);
        if(value <= 0) return ActionResult.none();

        int next = Math.min(EConstants.ENERGY_MAX.getInt(), character.getEnergy() + value);
        character.setEnergy((short) next);
        CharactersData.update(character);
        session.write(Statistic.getStatisticsMessage(character));
        return ActionResult.stats();
    }

    private static ActionResult addPermanentStat(Characters character, IoSession session, UseItemAction action) {
        String[] args = action.splitArgs();
        if(args.length < 2 || character.getStats() == null) return ActionResult.none();

        int effectId = parseInt(args[0], 0);
        int value = parseInt(args[1], 0);
        if(!isPermanentScrollStat(effectId) || value == 0) {
            logger.debug("UseItemService : stat permanente refusee effect={} value={}", effectId, value);
            return ActionResult.none();
        }

        character.getStats().add(effectId, value);
        if(effectId == EConstants.ADD_VITALITY.getInt()) {
            character.setLife((short) Math.min(character.getLifeMax(), character.getLife() + value));
        }
        CharactersData.update(character);
        session.write(Statistic.getStatisticsMessage(character));
        return ActionResult.stats();
    }

    private static boolean isPermanentScrollStat(int effectId) {
        return effectId == EConstants.ADD_STRENGTH.getInt()
            || effectId == EConstants.ADD_AGILITY.getInt()
            || effectId == EConstants.ADD_CHANCE.getInt()
            || effectId == EConstants.ADD_WISDOM.getInt()
            || effectId == EConstants.ADD_VITALITY.getInt()
            || effectId == EConstants.ADD_INTELLIGENCE.getInt();
    }

    private static ActionResult learnSpellPlaceholder(IoSession session, UseItemAction action) {
        logger.debug("UseItemService : apprentissage sort a brancher plus tard : {}", action);
        session.write("Im11");
        return ActionResult.none();
    }

    private static ActionResult learnEmotePlaceholder(IoSession session, UseItemAction action) {
        logger.debug("UseItemService : apprentissage emote a brancher plus tard : {}", action);
        session.write("Im11");
        return ActionResult.none();
    }

    /**
     * Type 228 dans le client 1.29 = GameAction visuelle.
     * Dans GameActions.as, case 228 parse : cell,effectFile,targetCell,anim,level
     * puis charge dofus.Constants.SPELLS_PATH + effectFile + ".swf".
     *
     * Dans la table use_item_actions fournie, les args 228 sont surtout des ids
     * simples comme 383..457, utilisés par les fées d'artifice / objets type 74.
     */
    private static ActionResult launchVisualEffect228(Characters character, UseItemAction action) {
        if(character == null || character.getCurrentMap() == null) return ActionResult.none();

        String effectId = action.getArgs() == null ? "" : action.getArgs().trim();
        if(effectId.length() == 0) return ActionResult.none();

        int cell = character.getCellId();
        // Format extra attendu par le client : targetCell,effectSwf,targetCell,anim,level
        String extra = cell + "," + effectId + "," + cell + ",0,1";
        String packet = "GA;228;" + character.getId() + ";" + extra;

        for(Characters actor : new ArrayList<Characters>(character.getCurrentMap().getActors().values())) {
            IoSession s = WorldData.getSessionByAccount().get(actor.getOwner());
            if(s != null && s.isConnected()) s.write(packet);
        }

        return ActionResult.executed();
    }

    private static boolean consumeOne(Characters character, IoSession session, Item item) {
        if(character == null || item == null || character.getInventory() == null) return false;
        long uid = item.getUid();
        boolean removed = character.getInventory().removeItem(uid, 1);
        if(!removed) return false;

        Item remaining = character.getInventory().getByUid(uid);
        if(remaining == null) {
            ItemsData.delete(uid);
            session.write(Inventory.buildORPacket(uid));
        } else {
            ItemsData.update(remaining);
            session.write(Inventory.buildOQPacket(remaining));
        }
        return true;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch(Exception e) { return fallback; }
    }

    private static final class ActionResult {
        final boolean executed;
        final boolean changedStats;
        final boolean changedInventory;

        private ActionResult(boolean executed, boolean changedStats, boolean changedInventory) {
            this.executed = executed;
            this.changedStats = changedStats;
            this.changedInventory = changedInventory;
        }

        static ActionResult none()     { return new ActionResult(false, false, false); }
        static ActionResult executed() { return new ActionResult(true, false, false); }
        static ActionResult stats()    { return new ActionResult(true, true, false); }
    }
}
