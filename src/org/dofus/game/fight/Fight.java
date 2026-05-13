package org.dofus.game.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.session.IoSession;
import org.dofus.constants.EConstants;
import org.dofus.database.objects.CharactersData;
import org.dofus.database.objects.ItemsData;
import org.dofus.database.objects.SpellsData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.items.Inventory;
import org.dofus.objects.items.Item;
import org.dofus.objects.items.ItemTemplate;
import org.dofus.objects.items.PetService;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.dofus.objects.spells.KnownSpell;
import org.dofus.objects.spells.SpellTemplate;
import org.dofus.objects.characters.Statistic;
import org.dofus.utils.MapCellDecoder;
import org.dofus.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fight {

    private static final Logger logger = LoggerFactory.getLogger(Fight.class);
    private static final AtomicInteger FIGHT_ID_GEN = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Fight> activeFights = new ConcurrentHashMap<>();

    private static final int PLACEMENT_DURATION_SEC = 30;
    private static final int FIGHT_TYPE_PVM = 0;

    public enum State { PLACEMENT, ACTIVE, FINISHED }

    private final int id;
    private final MapTemplate map;
    private final long createdAt = System.currentTimeMillis();
    private State state = State.PLACEMENT;

    private final LinkedHashMap<Integer, Fighter> fighters = new LinkedHashMap<>();
    private final List<IoSession> spectators = Collections.synchronizedList(new ArrayList<IoSession>());
    private MonsterGroup monsterGroup;

    private final FightTurn turn;
    private List<Fighter> turnOrder = new ArrayList<Fighter>();
    private int turnIndex = 0;
    private ScheduledFuture<?> placementTimer;

    private String team0Places = "";
    private String team1Places = "";
    private final Set<Short> team0Cells = new HashSet<Short>();
    private final Set<Short> team1Cells = new HashSet<Short>();

    public Fight(MapTemplate map) {
        this.id = FIGHT_ID_GEN.getAndIncrement();
        this.map = map;
        this.turn = new FightTurn(this);
        activeFights.put(this.id, this);
        preparePlacementCells();
        logger.info("Fight {} created on map {}", id, map.getId());
    }

    public void addFighter(Fighter fighter) {
        fighters.put(fighter.getId(), fighter);
    }

    public void setMonsterGroup(MonsterGroup group) {
        this.monsterGroup = group;
    }

    public Fighter getFighter(int id) {
        return fighters.get(id);
    }

    public List<Fighter> getFighters() {
        return new ArrayList<Fighter>(fighters.values());
    }

    public List<Fighter> getTeam(int teamId) {
        List<Fighter> team = new ArrayList<Fighter>();
        for(Fighter f : fighters.values()) {
            if(f.getTeamId() == teamId) team.add(f);
        }
        return team;
    }

    public void startPlacement() {
        if(state != State.PLACEMENT) return;

        sendFightStateToFighters(true);
        broadcast("GP" + team0Places + "|" + team1Places + "|0");
        broadcast(buildGMPacket());
        broadcast(buildCoordinatesPacket());

        cancelPlacementTimer();
        placementTimer = FightTurn.schedule(new Runnable() {
            public void run() {
                if(state == State.PLACEMENT) startFight();
            }
        }, PLACEMENT_DURATION_SEC, TimeUnit.SECONDS);
    }

    public void removeFighter(int fighterId) {
        Fighter removed = fighters.remove(fighterId);
        if(turnOrder != null) turnOrder.removeIf(f -> f.getId() == fighterId);
        if(removed != null) broadcast("GM|-" + fighterId);
        if(state == State.PLACEMENT && (getTeam(0).isEmpty() || getTeam(1).isEmpty())) cancelFight();
    }

    public void cancelFight() {
        if(state == State.FINISHED) return;
        state = State.FINISHED;
        cancelPlacementTimer();
        activeFights.remove(id);
        broadcast("GV");
        logger.info("Fight {} canceled during placement", id);
    }

    public boolean choosePlacementCell(Fighter fighter, short cell) {
        if(state != State.PLACEMENT || fighter == null) return false;
        if(!isAllowedPlacementCell(fighter, cell)) return false;
        if(isOccupiedByOther(fighter, cell)) return false;

        fighter.setCell(cell);
        broadcast("GIC|" + fighter.getId() + ";" + cell);
        return true;
    }

    public short pickPlacementCell(Fighter fighter, short fallback) {
        if(fighter == null) return fallback;
        Set<Short> allowed = fighter.getTeamId() == 0 ? team0Cells : team1Cells;
        for(Short cell : allowed) {
            if(cell != null && !isOccupiedByOther(fighter, cell.shortValue())) return cell.shortValue();
        }
        return fallback;
    }

    public void setReady(Fighter fighter, boolean ready) {
        if(state != State.PLACEMENT || fighter == null || fighter.getType() == Fighter.FighterType.MONSTER) return;
        fighter.setReady(ready);
        broadcast("GR" + (ready ? "1" : "0") + fighter.getId());
        if(ready && allPlayableFightersReady()) startFight();
    }

    public synchronized void startFight() {
        if(state != State.PLACEMENT) return;
        cancelPlacementTimer();

        state = State.ACTIVE;
        turnOrder = new ArrayList<Fighter>(fighters.values());
        Collections.sort(turnOrder, (a, b) -> Integer.compare(b.getInitiative(), a.getInitiative()));
        turnIndex = 0;

        broadcast("GS");
        broadcast("GTL|" + buildTurnList());
        nextTurn();
    }

    public void nextTurn() {
        if(state == State.FINISHED) return;
        if(checkWinCondition()) {
            endFight();
            return;
        }

        int checked = 0;
        Fighter next = null;
        while(checked < turnOrder.size()) {
            if(turnIndex >= turnOrder.size()) turnIndex = 0;
            Fighter candidate = turnOrder.get(turnIndex++);
            checked++;
            if(candidate.canPlay()) {
                next = candidate;
                break;
            }
        }

        if(next == null) {
            endFight();
            return;
        }

        turn.startTurn(next);
        if(next.getType() == Fighter.FighterType.MONSTER) {
            MonsterAI.playTurn(this, next);
        }
    }

    public void handleAction(Fighter fighter, int actionId, String args) {
        if(state != State.ACTIVE) return;
        if(fighter == null || fighter.isDisconnected()) return;
        if(turn.getCurrentFighter() == null || turn.getCurrentFighter().getId() != fighter.getId()) return;

        if(actionId == 1) {
            handleMove(fighter, args);
        } else if(actionId >= 300 && actionId < 600) {
            handleSpell(fighter, actionId - 300, args);
        } else {
            logger.debug("Fight {} ignored action {} args={}", new Object[] { id, actionId, args });
        }
    }

    private void handleMove(Fighter fighter, String pathStr) {
        if(pathStr == null || pathStr.length() < 2) return;

        int steps = Math.max(0, (pathStr.length() / 2) - 1);
        if(steps > 0 && !fighter.spendMP(steps)) return;

        short newCell = fighter.getCell();
        try {
            newCell = decodeCellBase64(pathStr.substring(pathStr.length() - 2));
        } catch(Exception ignored) {
            return;
        }
        if(!map.isValidCellId(newCell) || isOccupiedByOther(fighter, newCell)) return;

        fighter.setCell(newCell);
        broadcast("GA1;1;" + fighter.getId() + ";" + pathStr);
    }

    private void handleSpell(Fighter fighter, int spellId, String args) {
        SpellTemplate spell = SpellsData.getTemplate(spellId);
        if(spell == null) return;

        int spellLevel = getKnownSpellLevel(fighter, spellId);
        if(spellLevel <= 0) return;

        SpellTemplate.SpellLevel level = spell.getLevel(spellLevel);
        if(level == null || !fighter.spendAP(level.getApCost())) return;

        short targetCell;
        try {
            targetCell = parseCellArg(args);
        } catch(Exception e) {
            return;
        }

        Fighter target = findFighterOnCell(targetCell);
        if(target != null) {
            for(SpellTemplate.SpellEffect effect : level.getEffects()) {
                applyEffect(fighter, target, effect);
            }
        }

        broadcast("GA1;300;" + fighter.getId() + ";" + spellId + "," + targetCell + ","
                + spell.getSpritId() + "," + spellLevel + "," + targetCell + ",-1,1");

        if(checkWinCondition()) endFight();
    }

    private void applyEffect(Fighter caster, Fighter target, SpellTemplate.SpellEffect effect) {
        int effectId = effect.getEffectId();
        int value = effect.roll();
        int statBonus = getStatForElement(caster, effectIdToElement(effectId));
        int raw = value + statBonus / 10;

        if(effectId == 108) {
            int healed = target.heal(raw);
            broadcast("GA108;" + caster.getId() + ";" + target.getId() + ";" + healed);
            broadcast("GA306;" + caster.getId() + ";" + target.getId() + ";" + target.getCurrentLife());
            return;
        }

        if(effectId >= 91 && effectId <= 100) {
            int element = effectIdToElement(effectId);
            int dealt = target.takeDamage(raw, element);
            broadcast("GA" + effectId + ";" + caster.getId() + ";" + target.getId() + ";" + dealt);
            broadcast("GA306;" + caster.getId() + ";" + target.getId() + ";" + target.getCurrentLife());
            if(effectId >= 91 && effectId <= 95) {
                int healed = caster.heal(dealt / 2);
                if(healed > 0) {
                    broadcast("GA108;" + caster.getId() + ";" + caster.getId() + ";" + healed);
                    broadcast("GA306;" + caster.getId() + ";" + caster.getId() + ";" + caster.getCurrentLife());
                }
            }
            if(target.isDead()) {
                broadcast("GA103;" + caster.getId() + ";" + target.getId());
                broadcast("GA402;" + target.getId() + ";0");
            }
        }
    }

    private boolean checkWinCondition() {
        boolean t0alive = false;
        boolean t1alive = false;
        for(Fighter f : fighters.values()) {
            if(!f.isDead()) {
                if(f.getTeamId() == 0) t0alive = true;
                if(f.getTeamId() == 1) t1alive = true;
            }
        }
        return !t0alive || !t1alive;
    }

    private void endFight() {
        if(state == State.FINISHED) return;
        state = State.FINISHED;
        activeFights.remove(id);
        cancelPlacementTimer();
        if(turn.getTurnTimer() != null) turn.getTurnTimer().cancel(false);

        int winnerTeam = findWinnerTeam();
        RewardContext rewards = calculateAndApplyRewards(winnerTeam);

        broadcast(buildResultPacket(winnerTeam, rewards));
        restorePlayersAfterFight();

        if(monsterGroup != null) {
            org.dofus.utils.MapRespawnService.scheduleRespawn(map, monsterGroup);
        }

        logger.info("Fight {} ended winnerTeam={} xp={} kamas={} drops={}",
                new Object[] { id, winnerTeam, rewards.totalXp, rewards.totalKamas, rewards.totalDropCount() });
    }

    private RewardContext calculateAndApplyRewards(int winnerTeam) {
        RewardContext rewards = new RewardContext();
        if(winnerTeam != 0 || monsterGroup == null) return rewards;

        int prospection = calculateTeamProspection(0);
        for(MonsterGroup.MonsterEntry entry : monsterGroup.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if(grade == null) continue;
            rewards.totalXp += grade.getXpBase();
            rewards.totalKamas += DropTable.rollKamas(grade, prospection);
            rewards.allDrops.addAll(DropTable.roll(entry.getTemplate().getId(), prospection));
        }

        List<Fighter> winners = alivePlayers(getTeam(0));
        if(winners.isEmpty()) return rewards;

        long xpPerWinner = Math.max(0, rewards.totalXp / winners.size());
        long kamasPerWinner = Math.max(0, rewards.totalKamas / winners.size());

        for(Fighter f : winners) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr == null) continue;
            if(xpPerWinner > 0) chr.getExperience().add(xpPerWinner);
            if(kamasPerWinner > 0) chr.setKamas(chr.getKamas() + kamasPerWinner);
            rewards.xpByFighter.put(f.getId(), xpPerWinner);
            rewards.kamasByFighter.put(f.getId(), kamasPerWinner);
        }

        if(!rewards.allDrops.isEmpty()) {
            Fighter receiver = winners.get(0);
            rewards.dropsByFighter.put(receiver.getId(), new ArrayList<DropTable.DropResult>(rewards.allDrops));
            Characters chr = WorldData.getCharacterById(receiver.getId());
            IoSession sess = sessionFor(receiver);
            if(chr != null) {
                giveDrops(chr, sess, rewards.allDrops);
            }
        }

        for(Fighter f : winners) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr == null) continue;
            IoSession sess = sessionFor(f);
            if(kamasPerWinner > 0 && sess != null && sess.isConnected()) sess.write("Of+" + kamasPerWinner);
            if(sess != null && sess.isConnected()) org.dofus.utils.RegenService.start(chr);
            CharactersData.update(chr);
        }

        return rewards;
    }

    private void giveDrops(Characters chr, IoSession sess, List<DropTable.DropResult> drops) {
        for(DropTable.DropResult drop : drops) {
            ItemTemplate tpl = ItemsData.getTemplate(drop.templateId);
            if(tpl == null) continue;
            Item stacked = findStackable(chr.getInventory(), tpl);
            Item item = chr.getInventory().addItem(tpl, drop.quantity);
            if(stacked != null && stacked.getUid() == item.getUid()) ItemsData.update(item);
            else ItemsData.insert(chr.getId(), item);
            if(sess != null && sess.isConnected()) sess.write(Inventory.buildOAPacket(item));
        }
        if(sess != null && sess.isConnected())
            sess.write("Ow" + chr.getInventory().getUsedPods() + "|" + chr.getMaxPods());
    }

    private void restorePlayersAfterFight() {
        for(Fighter f : fighters.values()) {
            if(f.getType() != Fighter.FighterType.PLAYER) continue;
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr == null) continue;

            chr.setCurrentCell(f.getCell());
            chr.setLife((short)Math.max(1, f.getCurrentLife()));
            if(f.isDead()) PetService.onOwnerDeath(chr, sessionFor(f));
            IoSession session = sessionFor(f);
            if(session != null && session.isConnected()) map.addActor(chr);
            CharactersData.update(chr);
        }
    }

    public void handleDisconnect(int characterId) {
        Fighter fighter = fighters.get(characterId);
        if(fighter == null || state == State.FINISHED) return;
        fighter.setDisconnected(true);
        fighter.setReady(false);
        broadcast("GA950;" + characterId + ";1");
        if(turn.getCurrentFighter() != null && turn.getCurrentFighter().getId() == characterId) {
            turn.endTurn();
        }
    }

    public void reconnect(Characters character, IoSession session) {
        Fighter fighter = fighters.get(character.getId());
        if(fighter == null || state == State.FINISHED) return;
        fighter.setDisconnected(false);
        character.setCurrentCell(fighter.getCell());

        sendJoinPacket(session, state == State.PLACEMENT, false);
        session.write("GP" + team0Places + "|" + team1Places + "|" + fighter.getTeamId());
        session.write(buildGMPacket());
        session.write(buildCoordinatesPacket());
        if(state == State.ACTIVE) {
            session.write("GS");
            session.write("GTL|" + buildTurnList());
            if(turn.getCurrentFighter() != null)
                session.write("GTS" + turn.getCurrentFighter().getId() + "|" + (FightTurn.TURN_DURATION_SEC * 1000));
        }
        broadcast("GA950;" + character.getId() + ";0");
    }

    public void addSpectator(Characters character, IoSession session) {
        if(session == null || state == State.FINISHED) return;
        if(!spectators.contains(session)) spectators.add(session);
        sendJoinPacket(session, false, true);
        session.write("GP" + team0Places + "|" + team1Places + "|0");
        session.write(buildGMPacket());
        session.write(buildCoordinatesPacket());
        if(state == State.ACTIVE) {
            session.write("GS");
            session.write("GTL|" + buildTurnList());
            if(turn.getCurrentFighter() != null)
                session.write("GTS" + turn.getCurrentFighter().getId() + "|" + (FightTurn.TURN_DURATION_SEC * 1000));
        }
    }

    public void broadcast(String packet) {
        for(Fighter f : new ArrayList<Fighter>(fighters.values())) {
            IoSession session = sessionFor(f);
            if(session != null && session.isConnected()) session.write(packet);
        }
        synchronized(spectators) {
            spectators.removeIf(s -> s == null || !s.isConnected());
            for(IoSession spectator : spectators) spectator.write(packet);
        }
    }

    private void sendFightStateToFighters(boolean placement) {
        for(Fighter f : fighters.values()) {
            if(f.getType() != Fighter.FighterType.PLAYER) continue;
            IoSession session = sessionFor(f);
            if(session != null && session.isConnected()) sendJoinPacket(session, placement, false);
        }
    }

    private void sendJoinPacket(IoSession session, boolean placement, boolean spectator) {
        int timer = placement ? PLACEMENT_DURATION_SEC * 1000 : -1;
        session.write("GJK2|" + (spectator ? "0" : "1") + "|" + (placement ? "1" : "0") + "|"
                + (spectator ? "1" : "0") + "|" + timer + "|" + FIGHT_TYPE_PVM);
    }

    public String buildFightListEntry() {
        return id + ";" + createdAt + ";0,0," + getTeam(0).size() + ";1,0," + getTeam(1).size();
    }

    public String buildFightDetailsPacket() {
        StringBuilder sb = new StringBuilder("fD").append(id).append("|");
        appendFightDetailsTeam(sb, getTeam(0));
        sb.append("|");
        appendFightDetailsTeam(sb, getTeam(1));
        return sb.toString();
    }

    private void appendFightDetailsTeam(StringBuilder sb, List<Fighter> team) {
        for(Fighter f : team) {
            sb.append(';').append(resultNameData(f)).append('~').append(f.getLevel());
        }
    }

    private String buildGMPacket() {
        StringBuilder sb = new StringBuilder("GM");
        for(Fighter fighter : fighters.values()) {
            sb.append("|+");
            appendGMEntry(sb, fighter);
        }
        return sb.toString();
    }

    private void appendGMEntry(StringBuilder sb, Fighter fighter) {
        if(fighter.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(fighter.getId());
            if(chr == null) return;
            String accessories = chr.getInventory() != null ? chr.getInventory().buildAccessories(true) : "";
            sb.append(fighter.getCell()).append(';')
              .append(fighter.getOrientation().ordinal()).append(";0;")
              .append(fighter.getId()).append(';')
              .append(chr.getName()).append(';')
              .append(chr.getBreed().getId()).append(';')
              .append(chr.getSkin()).append('^').append(chr.getSize()).append(';')
              .append(chr.getGender()).append(';')
              .append(chr.getExperience().getLevel()).append(';')
              .append(chr.getAlignmentType()).append(",100,")
              .append(chr.getAlignment().getLevel()).append(",0,")
              .append(chr.getAlignment().getDishonor() > 0 ? 1 : 0).append(';')
              .append(StringUtils.toHexOrNegative(chr.getColor1())).append(';')
              .append(StringUtils.toHexOrNegative(chr.getColor2())).append(';')
              .append(StringUtils.toHexOrNegative(chr.getColor3())).append(';')
              .append(accessories).append(';')
              .append(fighter.getCurrentLife()).append(';')
              .append(fighter.getBaseAP()).append(';')
              .append(fighter.getBaseMP()).append(';')
              .append(fighter.getResNeutral()).append(';')
              .append(fighter.getResEarth()).append(';')
              .append(fighter.getResFire()).append(';')
              .append(fighter.getResWater()).append(';')
              .append(fighter.getResAir()).append(";0;0;")
              .append(fighter.getTeamId()).append(";0");
            return;
        }

        int gfx = fighter.getGfxId() > 0 ? fighter.getGfxId() : 31;
        sb.append(fighter.getCell()).append(';')
          .append(fighter.getOrientation().ordinal()).append(";0;")
          .append(fighter.getId()).append(';')
          .append(resultNameData(fighter)).append(';')
          .append("-2;")
          .append(gfx).append("^100;")
          .append(fighter.getLevel()).append(';')
          .append("-1;-1;-1;")
          .append("0,0,0,0;")
          .append(fighter.getCurrentLife()).append(';')
          .append(fighter.getBaseAP()).append(';')
          .append(fighter.getBaseMP()).append(';')
          .append(fighter.getResNeutral()).append(';')
          .append(fighter.getResEarth()).append(';')
          .append(fighter.getResFire()).append(';')
          .append(fighter.getResWater()).append(';')
          .append(fighter.getResAir()).append(";0;0;")
          .append(fighter.getTeamId());
    }

    private String buildCoordinatesPacket() {
        StringBuilder sb = new StringBuilder("GIC");
        for(Fighter f : fighters.values()) {
            sb.append('|').append(f.getId()).append(';').append(f.getCell());
        }
        return sb.toString();
    }

    private String buildTurnList() {
        StringBuilder sb = new StringBuilder();
        for(Fighter f : turnOrder) {
            if(sb.length() > 0) sb.append('|');
            sb.append(f.getId());
        }
        return sb.toString();
    }

    private String buildResultPacket(int winnerTeam, RewardContext rewards) {
        int senderId = firstPlayerId();
        long duration = Math.max(0, System.currentTimeMillis() - createdAt);
        StringBuilder sb = new StringBuilder("GE");
        sb.append(duration).append('|').append(senderId).append('|').append(FIGHT_TYPE_PVM);
        for(Fighter f : fighters.values()) {
            sb.append('|').append(buildResultEntry(f, winnerTeam, rewards));
        }
        return sb.toString();
    }

    private String buildResultEntry(Fighter f, int winnerTeam, RewardContext rewards) {
        int resultType = f.getTeamId() == winnerTeam ? 2 : 0;
        String drops = buildDropList(rewards.dropsByFighter.get(f.getId()));
        long kama = rewards.kamasByFighter.getOrDefault(f.getId(), 0L);

        if(f.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(f.getId());
            long min = chr != null ? chr.getExperience().min() : 0;
            long xp = chr != null ? chr.getExperience().getExperience() : 0;
            long max = chr != null ? chr.getExperience().max() : 0;
            long winXp = rewards.xpByFighter.getOrDefault(f.getId(), 0L);
            int level = chr != null ? chr.getExperience().getLevel() : f.getLevel();
            String name = chr != null ? chr.getName() : f.getName();
            return resultType + ";" + f.getId() + ";" + name + ";" + level + ";" + (f.isDead() ? 1 : 0)
                    + ";" + min + ";" + xp + ";" + max + ";" + winXp + ";0;0;" + drops + ";" + kama;
        }

        return resultType + ";" + f.getId() + ";" + resultNameData(f) + ";" + f.getLevel() + ";"
                + (f.isDead() ? 1 : 0) + ";0;0;0;0;0;0;;0";
    }

    private String buildDropList(List<DropTable.DropResult> drops) {
        if(drops == null || drops.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(DropTable.DropResult drop : drops) {
            if(sb.length() > 0) sb.append(',');
            sb.append(drop.templateId).append('~').append(drop.quantity);
        }
        return sb.toString();
    }

    private void preparePlacementCells() {
        String places = map != null ? map.getPlaces() : null;
        String[] sides = places != null ? places.split("\\|", -1) : new String[0];
        if(sides.length >= 2) {
            team0Places = sides[0];
            team1Places = sides[1];
            decodePlacementSide(team0Places, team0Cells);
            decodePlacementSide(team1Places, team1Cells);
        }
    }

    public void ensurePlacementFallbacks(short attackerCell, short defenderCell) {
        if(team0Cells.isEmpty()) {
            List<Short> cells = fallbackCells(attackerCell);
            team0Places = encodeCells(cells);
            team0Cells.addAll(cells);
        }
        if(team1Cells.isEmpty()) {
            List<Short> cells = fallbackCells(defenderCell);
            team1Places = encodeCells(cells);
            team1Cells.addAll(cells);
        }
    }

    private List<Short> fallbackCells(short origin) {
        List<Short> cells = new ArrayList<Short>();
        int[] offsets = new int[] { 0, 1, -1, 14, -14, 15, -15, 28, -28 };
        for(int offset : offsets) {
            short cell = (short)(origin + offset);
            if(map.isValidCellId(cell) && !cells.contains(cell)) cells.add(cell);
            if(cells.size() >= 8) break;
        }
        if(cells.isEmpty()) cells.add(origin);
        return cells;
    }

    private void decodePlacementSide(String encoded, Set<Short> out) {
        if(encoded == null) return;
        for(Short cell : MapCellDecoder.decodePlacementCells(encoded)) out.add(cell);
    }

    private String encodeCells(List<Short> cells) {
        StringBuilder sb = new StringBuilder();
        for(Short cell : cells) {
            if(cell != null) sb.append(encodeCellBase64(cell.shortValue()));
        }
        return sb.toString();
    }

    private boolean isAllowedPlacementCell(Fighter fighter, short cell) {
        Set<Short> allowed = fighter.getTeamId() == 0 ? team0Cells : team1Cells;
        return allowed.isEmpty() || allowed.contains(cell);
    }

    private boolean isOccupiedByOther(Fighter fighter, short cell) {
        for(Fighter f : fighters.values()) {
            if(f.getId() != fighter.getId() && !f.isDead() && f.getCell() == cell) return true;
        }
        return false;
    }

    private boolean allPlayableFightersReady() {
        for(Fighter f : fighters.values()) {
            if(f.getType() == Fighter.FighterType.MONSTER) continue;
            if(!f.isDead() && !f.isReady()) return false;
        }
        return true;
    }

    private int findWinnerTeam() {
        for(Fighter f : fighters.values()) {
            if(!f.isDead()) return f.getTeamId();
        }
        return -1;
    }

    private List<Fighter> alivePlayers(List<Fighter> team) {
        List<Fighter> result = new ArrayList<Fighter>();
        for(Fighter f : team) {
            if(f.getType() == Fighter.FighterType.PLAYER && !f.isDead()) result.add(f);
        }
        return result;
    }

    private int calculateTeamProspection(int teamId) {
        int total = 0;
        for(Fighter f : getTeam(teamId)) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr != null) {
                total += Statistic.totalWithEquipment(chr, EConstants.ADD_PROSPECTION.getInt());
                total += f.getChance() / 10;
            }
        }
        return Math.max(100, total);
    }

    private Fighter findFighterOnCell(short cell) {
        for(Fighter f : fighters.values()) {
            if(!f.isDead() && f.getCell() == cell) return f;
        }
        return null;
    }

    private int getKnownSpellLevel(Fighter fighter, int spellId) {
        if(fighter.getType() != Fighter.FighterType.PLAYER) return 1;
        Characters character = WorldData.getCharacterById(fighter.getId());
        if(character == null) return 0;
        KnownSpell spell = character.getKnownSpell(spellId);
        return spell == null ? 0 : spell.getLevel();
    }

    private int getStatForElement(Fighter f, int element) {
        switch(element) {
            case 1: return f.getStrength();
            case 2: return f.getChance();
            case 3: return f.getIntel();
            case 4: return f.getAgility();
            default: return f.getStrength();
        }
    }

    private int effectIdToElement(int effectId) {
        switch(effectId) {
            case 91:
            case 96: return 2; // water
            case 92:
            case 97: return 1; // earth
            case 93:
            case 98: return 4; // air
            case 94:
            case 99: return 3; // fire
            case 95:
            case 100: return 0; // neutral
            default: return 0;
        }
    }

    private short parseCellArg(String args) {
        if(args == null) throw new IllegalArgumentException("missing cell");
        String value = args;
        if(value.contains("|")) value = value.substring(value.lastIndexOf('|') + 1);
        if(value.contains(";")) value = value.substring(value.lastIndexOf(';') + 1);
        value = value.trim();
        if(value.length() == 2 && !isInteger(value)) return decodeCellBase64(value);
        return Short.parseShort(value);
    }

    private static boolean isInteger(String value) {
        if(value == null || value.isEmpty()) return false;
        for(int i = 0; i < value.length(); i++) {
            if(!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static short decodeCellBase64(String s) {
        if(s == null || s.length() < 2) throw new IllegalArgumentException("cell too short");
        int high = StringUtils.HASH.indexOf(s.charAt(0));
        int low = StringUtils.HASH.indexOf(s.charAt(1));
        if(high < 0 || low < 0) throw new IllegalArgumentException("bad cell encoding");
        return (short)(high * 64 + low);
    }

    static String encodeCellBase64(short cellId) {
        return String.valueOf(StringUtils.HASH.charAt(cellId / 64)) + StringUtils.HASH.charAt(cellId % 64);
    }

    private IoSession sessionFor(Fighter fighter) {
        if(fighter == null || fighter.getType() != Fighter.FighterType.PLAYER) return null;
        Characters chr = WorldData.getCharacterById(fighter.getId());
        if(chr == null) return null;
        return WorldData.getSessionByAccount().get(chr.getOwner());
    }

    private int firstPlayerId() {
        for(Fighter f : fighters.values()) {
            if(f.getType() == Fighter.FighterType.PLAYER) return f.getId();
        }
        return 0;
    }

    private String resultNameData(Fighter f) {
        if(f.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(f.getId());
            return chr != null ? chr.getName() : f.getName();
        }
        if(f.getTemplateId() > 0) return String.valueOf(f.getTemplateId());
        if(f.getId() >= 1000) return String.valueOf(f.getId() / 1000);
        return f.getName();
    }

    private void cancelPlacementTimer() {
        if(placementTimer != null && !placementTimer.isDone()) placementTimer.cancel(false);
        placementTimer = null;
    }

    private static Item findStackable(Inventory inventory, ItemTemplate template) {
        if(template.getTypeId() < 48) return null;
        for(Item item : inventory.getBag()) {
            if(item.getTemplate().getId() == template.getId()) return item;
        }
        return null;
    }

    public int getId() { return id; }
    public MapTemplate getMap() { return map; }
    public State getState() { return state; }
    public FightTurn getTurn() { return turn; }

    public static Fight getFight(int fightId) { return activeFights.get(fightId); }

    public static Map<Integer, Fight> getActiveFights() {
        return Collections.unmodifiableMap(activeFights);
    }

    private static final class RewardContext {
        long totalXp = 0;
        long totalKamas = 0;
        final List<DropTable.DropResult> allDrops = new ArrayList<DropTable.DropResult>();
        final Map<Integer, Long> xpByFighter = new LinkedHashMap<Integer, Long>();
        final Map<Integer, Long> kamasByFighter = new LinkedHashMap<Integer, Long>();
        final Map<Integer, List<DropTable.DropResult>> dropsByFighter =
                new LinkedHashMap<Integer, List<DropTable.DropResult>>();

        int totalDropCount() {
            int count = 0;
            for(List<DropTable.DropResult> drops : dropsByFighter.values()) count += drops.size();
            return count;
        }
    }
}
