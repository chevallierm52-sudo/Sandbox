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
import org.dofus.objects.actors.EOrientation;
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
    /** Compteur global d'actions de jeu : chaque GA<gaId> doit être unique pour éviter
     *  que le client 1.29 ne filtre les actions consécutives portant le même id. */
    private static final AtomicInteger GA_ID = new AtomicInteger(1);

    private static int nextGaId() { return GA_ID.getAndIncrement(); }
    /** Variante publique pour MonsterAI et autres classes du package combat. */
    public static int nextGaPacketId() { return GA_ID.getAndIncrement(); }

    private static final int PLACEMENT_DURATION_SEC = 30;
    /** Type combat envoyé dans GE : AncestraR utilise 0 (CHALLENGE) pour PvM, pas 4. */
    private static final int FIGHT_TYPE_PVM = 4;            // pour GJK
    private static final int FIGHT_GE_TYPE_CHALLENGE = 0;   // pour GE
    private static final int FIGHT_SPRITE_REFRESH_DELAY_MS = 100;
    /** Délai laissé au client pour jouer l'animation d'un sort avant d'appliquer les effets. */
    private static final long SPELL_ANIMATION_MS = 700L;
    /** Marqueurs GAF (Game Action Finished) — convention AncestraR par type d'action. */
    private static final int GAF_SPELL = 0;
    private static final int GAF_MELEE = 1;
    private static final int GAF_MOVE  = 2;

    public enum State { PLACEMENT, ACTIVE, FINISHED }

    private final int id;
    private final MapTemplate map;
    private final long createdAt = System.currentTimeMillis();
    /** Marquage du passage en phase ACTIVE — sert au {@code time} du packet GE
     *  (AncestraR : on n'inclut PAS la phase placement dans la durée du combat). */
    private long       startedAt = 0L;
    private State state = State.PLACEMENT;

    private final LinkedHashMap<Integer, Fighter> fighters = new LinkedHashMap<>();
    private final List<IoSession> spectators = Collections.synchronizedList(new ArrayList<IoSession>());
    private MonsterGroup monsterGroup;
    private final FightTurn turn;
    private List<Fighter> turnOrder = new ArrayList<>();
    private int turnIndex = 0;
    private ScheduledFuture<?> placementTimer;

    private String team0Places = "";
    private String team1Places = "";
    private final Set<Short> team0Cells = new HashSet<>();
    private final Set<Short> team1Cells = new HashSet<>();

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
        List<Fighter> team = new ArrayList<>();
        for (Fighter f : fighters.values()) {
            if (f.getTeamId() == teamId) team.add(f);
        }
        return team;
    }

    public void startPlacement() {
        if (state != State.PLACEMENT) return;

        sendFightStateToFighters(true);
        broadcast("GP" + team0Places + "|" + team1Places + "|0");
        broadcastFightSprites();
        broadcast(buildCoordinatesPacket());
        broadcast("GDK");
        scheduleFightSpritesRefresh();

        cancelPlacementTimer();
        placementTimer = FightTurn.schedule(new Runnable() {
            public void run() {
                if (Fight.this.getState() == State.PLACEMENT) Fight.this.startFight();
            }
        }, PLACEMENT_DURATION_SEC, TimeUnit.SECONDS);
    }

    public void removeFighter(int fighterId) {
        Fighter removed = fighters.remove(fighterId);
        if (turnOrder != null) turnOrder.removeIf(f -> f.getId() == fighterId);
        if (removed != null) {
            broadcast("GM|-" + fighterId);
            if (removed.getType() == Fighter.FighterType.PLAYER) {
                Characters chr = WorldData.getCharacterById(fighterId);
                IoSession sess = sessionFor(removed);
                if (chr != null && sess != null && sess.isConnected()) {
                    chr.setLife((short) Math.max(1, removed.getCurrentLife()));
                    map.addActor(chr);
                }
            }
        }
        if (state == State.PLACEMENT && (getTeam(0).isEmpty() || getTeam(1).isEmpty())) cancelFight();
    }

    public void cancelFight() {
        if (state == State.FINISHED) return;
        state = State.FINISHED;
        cancelPlacementTimer();
        activeFights.remove(id);
        broadcast("GV");
        // En placement, on garde la cellule roleplay d'origine de chaque joueur (chr.getCurrentCell()).
        // restorePlayersAfterFight l'écraserait avec la cellule de placement combat.
        for (Fighter f : fighters.values()) {
            if (f.getType() != Fighter.FighterType.PLAYER) continue;
            Characters chr = WorldData.getCharacterById(f.getId());
            IoSession session = sessionFor(f);
            if (chr != null && session != null && session.isConnected()) map.addActor(chr);
        }
        if (monsterGroup != null) {
            map.addMonsterGroup(monsterGroup);
            broadcastOnMap("GM|+" + monsterGroup.toGMEntry(), null);
        }
        logger.info("Fight {} canceled during placement, group={} restored={}",
                new Object[] { id, monsterGroup != null ? monsterGroup.getId() : -1, monsterGroup != null });
    }

    public void handleAbandon(Fighter fighter) {
        if (fighter == null || state != State.ACTIVE) return;
        // Force la mort en infligeant tous les PV restants (résistance neutre clampée).
        if (!fighter.isDead()) fighter.takeDamage(99999, 0);
        // Format AncestraR exact (logs de combat) : un seul GA;103, pas de gaId, pas de GA;402.
        // Pas de syncFightState non plus — le GIC/GTM ne sert à rien si on quitte.
        broadcast("GA;103;" + fighter.getId() + ";" + fighter.getId());
        if (turn.getCurrentFighter() != null && turn.getCurrentFighter().getId() == fighter.getId()) {
            turn.endTurn();
        }
        if (checkWinCondition()) endFight();
    }

    private void broadcastOnMap(String packet, Characters except) {
        if (map == null) return;
        for (Characters actor : new ArrayList<Characters>(map.getActors().values())) {
            if (actor == null || actor == except) continue;
            IoSession actorSess = WorldData.getSessionByAccount().get(actor.getOwner());
            if (actorSess != null && actorSess.isConnected()) actorSess.write(packet);
        }
    }

    public boolean choosePlacementCell(Fighter fighter, short cell) {
        if (state != State.PLACEMENT || fighter == null) return false;
        if (!isAllowedPlacementCell(fighter, cell)) return false;
        if (isOccupiedByOther(fighter, cell)) return false;
        fighter.setCell(cell);
        broadcast("GIC|" + fighter.getId() + ";" + cell);
        return true;
    }

    public short pickPlacementCell(Fighter fighter, short fallback) {
        if (fighter == null) return fallback;
        Set<Short> allowed = fighter.getTeamId() == 0 ? team0Cells : team1Cells;
        for (Short cell : allowed) {
            if (cell != null && !isOccupiedByOther(fighter, cell.shortValue())) return cell.shortValue();
        }
        return fallback;
    }

    public void setReady(Fighter fighter, boolean ready) {
        if (state != State.PLACEMENT || fighter == null || fighter.getType() == Fighter.FighterType.MONSTER) return;
        fighter.setReady(ready);
        broadcast("GR" + (ready ? "1" : "0") + fighter.getId());
        if (ready && allPlayableFightersReady()) startFight();
    }

    public synchronized void startFight() {
        if (state != State.PLACEMENT) return;
        cancelPlacementTimer();
        state = State.ACTIVE;
        startedAt = System.currentTimeMillis();
        turnOrder = new ArrayList<Fighter>(fighters.values());
        Collections.sort(turnOrder, (a, b) -> Integer.compare(b.getInitiative(), a.getInitiative()));
        turnIndex = 0;

        broadcastFightSprites();
        broadcast(buildCoordinatesPacket());
        broadcast("GS");
        scheduleFightSpritesRefresh();
        broadcast("GTL|" + buildTurnList());
        broadcast(buildTurnStatusPacket());
        nextTurn();
    }

    public void nextTurn() {
        if (state == State.FINISHED) return;
        if (checkWinCondition()) {
            endFight();
            return;
        }

        int checked = 0;
        Fighter next = null;
        while (checked < turnOrder.size()) {
            if (turnIndex >= turnOrder.size()) turnIndex = 0;
            Fighter candidate = turnOrder.get(turnIndex++);
            checked++;
            if (candidate.canPlay()) {
                next = candidate;
                break;
            }
        }

        if (next == null) {
            endFight();
            return;
        }

        turn.startTurn(next);
        if (next.getType() == Fighter.FighterType.MONSTER) {
            final Fighter monster = next;
            FightTurn.schedule(new Runnable() {
                public void run() {
                    FightTurn activeTurn = Fight.this.getTurn();
                    if (Fight.this.getState() == State.ACTIVE
                            && activeTurn.getCurrentFighter() != null
                            && activeTurn.getCurrentFighter().getId() == monster.getId()) {
                        MonsterAI.playTurn(Fight.this, monster);
                    }
                }
            }, 650, TimeUnit.MILLISECONDS);
        }
    }

    public void handleAction(Fighter fighter, int actionId, String args) {
        if (state != State.ACTIVE) return;
        if (fighter == null || fighter.isDisconnected()) return;
        if (turn.getCurrentFighter() == null || turn.getCurrentFighter().getId() != fighter.getId()) return;

        if (actionId == 1) {
            handleMove(fighter, args);
        } else if (actionId >= 300 && actionId < 600) {
            handleSpell(fighter, actionId - 300, args);
        } else {
            logger.debug("Fight {} ignored action {} args={}", new Object[] { id, actionId, args });
        }
    }

    private void handleMove(Fighter fighter, String pathStr) {
        if (pathStr == null || pathStr.length() < 3) return;

        // Format Dofus 1.29 : chaque saut = 1 char direction + 2 chars cellule = 3 chars/step.
        // Le path NE contient PAS la cellule de départ.
        int requestedSteps = pathStr.length() / 3;
        if (requestedSteps <= 0) return;

        // Tronque le path au MP disponible (StarLoco isValidPath : si nStep > PM, on rejette;
        // ici on tronque intelligemment pour ne pas frustrer le joueur qui clique loin).
        int steps = Math.min(requestedSteps, fighter.getCurrentMP());
        if (steps <= 0) return;
        String effectivePath = pathStr.substring(0, steps * 3);

        short newCell;
        try {
            newCell = decodeCellBase64(effectivePath.substring(effectivePath.length() - 2));
        } catch (Exception ignored) {
            return;
        }

        if (!map.isValidCellId(newCell) || isOccupiedByOther(fighter, newCell)) return;
        if (!fighter.spendMP(steps)) return;

        // Animation côté client : path doit commencer par "a" + cellule de départ encodée
        // (StarLoco Fight.onFighterMovement l.3066, sinon le sprite TP au lieu d'animer).
        // Le gaId est INCRÉMENTÉ (cf nextGaId) : le client 1.29 filtre/déduplique
        // les GA<id> consécutifs portant le même id → animations ignorées en série.
        // GAS<actorId> précède le GA;1 — sans lui, certains clients 1.29 ignorent l'animation
        // (AncestraR Fight.onFighterMovement l.3062 l'envoie systématiquement pour les players).
        short fromCell = fighter.getCell();
        String animPath = "a" + encodeCellBase64(fromCell) + effectivePath;
        fighter.setCell(newCell);
        // Met à jour l'orientation du joueur d'après la direction du dernier step :
        // le 3e dernier char du `effectivePath` est le `dir` char de la dernière transition.
        // Sans ça le sprite garde son orientation de placement après chaque déplacement.
        if (effectivePath.length() >= 3) {
            char lastDir = effectivePath.charAt(effectivePath.length() - 3);
            EOrientation finalOrient = EOrientation.valueOf(StringUtils.HASH.indexOf(lastDir));
            if (finalOrient != null) fighter.setOrientation(finalOrient);
        }
        broadcast("GAS" + fighter.getId());
        broadcast("GA" + nextGaId() + ";1;" + fighter.getId() + ";" + animPath);

        // Anim côté client : ~650 ms/case en roleplay (cf packets.log intervalle GA;1 → GKK).
        // Séquence AncestraR après le mouvement :
        //   1. GA;129;<id>;<id>,-N      (perte PM, PAS de gaId)
        //   2. GAF2|<id>                (Game Action Finished pour mouvement)
        // PAS de GIC ni de GTM ici — le client met à jour les positions via le path lui-même.
        // GTM/GTR sont envoyés plus tard par endTurn() après que le client a ACK le GAF.
        final int finalSteps = steps;
        final int fighterId = fighter.getId();
        long animMs = Math.max(900L, steps * 700L);
        FightTurn.schedule(() -> {
            if (state == State.FINISHED) return;
            Fighter f = fighters.get(fighterId);
            if (f == null || f.isDead()) return;
            broadcast("GA;129;" + fighterId + ";" + fighterId + ",-" + finalSteps);
            broadcast("GAF" + GAF_MOVE + "|" + fighterId);
        }, animMs, TimeUnit.MILLISECONDS);
    }

    private List<Fighter> adjacentEnemies(Fighter ref, short fromCell) {
        List<Fighter> result = new ArrayList<Fighter>();
        for (Fighter f : fighters.values()) {
            if (f.getId() == ref.getId() || f.isDead()) continue;
            if (f.getTeamId() == ref.getTeamId()) continue;
            int diff = Math.abs(f.getCell() - fromCell);
            if (diff == 1 || diff == MAP_GRID_WIDTH) result.add(f);
        }
        return result;
    }

    private double tackleEscapeChance(Fighter fleeing, List<Fighter> taclers) {
        int agiTotal = 0;
        for (Fighter t : taclers) agiTotal += Math.max(0, t.getAgility());
        int agiFlee = Math.max(0, fleeing.getAgility());
        double chance = (agiFlee + 25.0) / (agiFlee + agiTotal + 50.0);
        return Math.max(0.10, Math.min(0.90, chance));
    }

    private static final int MAP_GRID_WIDTH = 14;

    private int manhattanDistance(short a, short b) {
        int ax = a % MAP_GRID_WIDTH;
        int ay = a / MAP_GRID_WIDTH;
        int bx = b % MAP_GRID_WIDTH;
        int by = b / MAP_GRID_WIDTH;
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private boolean isInStraightLine(short a, short b) {
        int ax = a % MAP_GRID_WIDTH;
        int ay = a / MAP_GRID_WIDTH;
        int bx = b % MAP_GRID_WIDTH;
        int by = b / MAP_GRID_WIDTH;
        return ax == bx || ay == by;
    }

    private void handleSpell(Fighter fighter, int spellId, String args) {
        SpellTemplate spell = SpellsData.getTemplate(spellId);
        if (spell == null) return;

        int spellLevel = getKnownSpellLevel(fighter, spellId);
        if (spellLevel <= 0) return;

        SpellTemplate.SpellLevel level = spell.getLevel(spellLevel);
        if (level == null) return;

        final short targetCell;
        try {
            targetCell = parseCellArg(args);
        } catch (Exception e) {
            return;
        }
        if (!map.isValidCellId(targetCell)) return;

        int apCost = level.getApCost();
        if (!fighter.spendAP(apCost)) return;

        // Ordre AncestraR exact (logs de combat capturés ligne 437-451) :
        //   GAS<casterId>
        //   GA;300;<casterId>;<spellId>,<targetCell>,<sprite>,<level>,<x>,<y>,<crit>
        //   GA;100;<casterId>;<targetId>,-<dmg>   ← effets dégâts/soin IMMÉDIATS (queue client)
        //   GA;103;<casterId>;<targetId>          ← si cible morte
        //   GA;102;<casterId>;<casterId>,-<apCost> ← perte PA APRÈS les effets
        //   GAF0|<casterId>                       ← Game Action Finished (signal fin serveur)
        //
        // PAS de délai entre les packets : le client met les animations en queue interne et
        // les joue dans l'ordre. Un délai 700ms côté serveur séparait l'anim sort des
        // dégâts → animation cassée car les effets apparaissaient AVANT l'anim sort finie.
        // PAS de gaId sur les follow-up GA;... (AncestraR convention).
        broadcast("GAS" + fighter.getId());
        broadcast("GA;300;" + fighter.getId() + ";"
                + spellId + "," + targetCell + "," + spell.getSpritId() + "," + spellLevel
                + ",0,0,1");

        // Application immédiate des effets (dégâts/soin/mort) — le client gère la queue
        // d'animations côté UI.
        Fighter target = findFighterOnCell(targetCell);
        if (target != null) {
            for (SpellTemplate.SpellEffect effect : level.getEffects()) {
                applyEffect(fighter, target, effect);
            }
        }

        // Perte PA envoyée APRÈS les effets (ordre Ancestra l.450) — sinon le client peut
        // démarrer l'anim "perte PA" en plein milieu de l'animation du sort.
        if (apCost > 0) broadcast("GA;102;" + fighter.getId() + ";"
                + fighter.getId() + ",-" + apCost);

        // GAF0|<actor> — Game Action Finished. Le client peut envoyer son GKK0 quand
        // toutes les animations sont jouées, puis on passera au tour suivant.
        broadcast("GAF" + GAF_SPELL + "|" + fighter.getId());

        if (checkWinCondition()) endFight();
    }

    private void applyEffect(Fighter caster, Fighter target, SpellTemplate.SpellEffect effect) {
        int effectId = effect.getEffectId();
        int value = effect.roll();
        int statBonus = getStatForElement(caster, effectIdToElement(effectId));
        int raw = value + statBonus / 10;

        // Format AncestraR : les effets follow-up (100, 103, 108) sont envoyés SANS gaId :
        //   GA;<effectId>;<caster>;<target>,<delta>
        // Delta négatif = perte (100, dégâts), positif = gain (108, soin).
        if (effectId == 108) {
            int healed = target.heal(raw);
            broadcast("GA;108;" + caster.getId() + ";" + target.getId() + "," + healed);
            return;
        }

        if (effectId >= 91 && effectId <= 100) {
            int element = effectIdToElement(effectId);
            int dealt = target.takeDamage(raw, element);

            broadcast("GA;100;" + caster.getId() + ";" + target.getId() + ",-" + dealt);

            if (effectId >= 91 && effectId <= 95) {
                int healed = caster.heal(dealt / 2);
                if (healed > 0) {
                    broadcast("GA;108;" + caster.getId() + ";" + caster.getId() + "," + healed);
                }
            }

            if (target.isDead()) {
                // Format AncestraR : GA;103;<caster>;<target> (PAS de gaId, PAS de GA;402).
                broadcast("GA;103;" + caster.getId() + ";" + target.getId());
            }
        }
    }

    private boolean checkWinCondition() {
        boolean t0alive = false;
        boolean t1alive = false;
        for (Fighter f : fighters.values()) {
            if (!f.isDead()) {
                if (f.getTeamId() == 0) t0alive = true;
                if (f.getTeamId() == 1) t1alive = true;
            }
        }
        return !t0alive || !t1alive;
    }

    private void endFight() {
        if (state == State.FINISHED) return;
        state = State.FINISHED;
        activeFights.remove(id);
        cancelPlacementTimer();
        if (turn.getTurnTimer() != null) turn.getTurnTimer().cancel(false);

        int winnerTeam = findWinnerTeam();
        RewardContext rewards = calculateAndApplyRewards(winnerTeam);
        // Séquence AncestraR fin de combat (logs vérifiés) :
        //   1. GE  : panneau résultat
        //   2. fC0 : fight count = 0
        //   3. GM  : re-spawn du groupe mob sur la map (background du panneau)
        //   4. ILS1000 : "Information Lock Status" — signale que le client peut fermer
        //   5. (PAS de GV ici — sinon le panneau se ferme avant d'être vu)
        // Le client envoie GKK0 quand l'utilisateur clique "Fermer" → on lui sort
        // alors la map via restorePlayersAfterFight (déclenché sur GC1 ultérieur).
        broadcast(buildResultPacket(winnerTeam, rewards));
        broadcast("fC0");
        restorePlayersAfterFight();
        broadcast("ILS1000");
        if (monsterGroup != null) {
            org.dofus.utils.MapRespawnService.scheduleRespawn(map, monsterGroup);
        }
        logger.info("Fight {} ended winnerTeam={} xp={} kamas={} drops={}",
                new Object[] { id, winnerTeam, rewards.totalXp, rewards.totalKamas, rewards.totalDropCount() });
    }

    private RewardContext calculateAndApplyRewards(int winnerTeam) {
        RewardContext rewards = new RewardContext();
        if (winnerTeam != 0 || monsterGroup == null) return rewards;

        List<Fighter> winners = alivePlayers(getTeam(0));
        if (winners.isEmpty()) return rewards;

        // --- Étape 1 : PP de groupe (somme prospection des winners) ---
        int groupPP = 0;
        for (Fighter w : winners) {
            Characters chr = WorldData.getCharacterById(w.getId());
            if (chr != null) {
                groupPP += Statistic.totalWithEquipment(chr, EConstants.ADD_PROSPECTION.getInt());
                groupPP += w.getChance() / 10;
            }
        }
        groupPP = Math.max(100, groupPP);

        // --- Étape 2 : agréger XP + kamas + drops bruts (avec PP boost + starBonus du groupe) ---
        int totalMinKamas = 0;
        int totalMaxKamas = 0;
        int starBonusPct = monsterGroup != null ? monsterGroup.getStarBonus() : 0;
        // Le groupPP est boosté par le starBonus pour les drops (AncestraR : factChalDrop += starBonus)
        int effectivePP = (int) ((long) groupPP * (100 + starBonusPct) / 100L);
        List<DropTable.DropEntry> possibleDrops = new ArrayList<DropTable.DropEntry>();
        for (MonsterGroup.MonsterEntry entry : monsterGroup.getMembers()) {
            MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
            if (grade == null) continue;
            rewards.totalXp += grade.getXpBase();
            totalMinKamas += grade.getKamasMin();
            totalMaxKamas += grade.getKamasMax();
            for (DropTable.DropEntry drop : DropTable.getDrops(entry.getTemplate().getId())) {
                // Filtre AncestraR : drop ignoré si la PP groupe est sous le seuil minProsp.
                if (drop.minProsp > effectivePP) continue;
                int boostedRate = (int) Math.min(DropTable.MAX_RATE, (long) drop.rate * effectivePP / 100L);
                possibleDrops.add(new DropTable.DropEntry(drop.templateId, boostedRate,
                        drop.qtyMin, drop.qtyMax, drop.minProsp, drop.max));
            }
        }

        // --- Étape 3 : trier winners par PP DESC (le plus PP loot en premier) ---
        List<Fighter> sortedWinners = new ArrayList<Fighter>(winners);
        sortedWinners.sort((a, b) -> {
            Characters ca = WorldData.getCharacterById(a.getId());
            Characters cb = WorldData.getCharacterById(b.getId());
            int ppA = ca != null ? Statistic.totalWithEquipment(ca, EConstants.ADD_PROSPECTION.getInt()) : 0;
            int ppB = cb != null ? Statistic.totalWithEquipment(cb, EConstants.ADD_PROSPECTION.getInt()) : 0;
            return Integer.compare(ppB, ppA);
        });

        // --- Étape 4 : distribution drops (algo AncestraR Fight.java:2913) ---
        int maxItemsPerPerso = Math.max(1, possibleDrops.size() / Math.max(1, sortedWinners.size()));
        for (Fighter winner : sortedWinners) {
            if (possibleDrops.isEmpty()) break;
            List<DropTable.DropEntry> shuffled = new ArrayList<DropTable.DropEntry>(possibleDrops);
            Collections.shuffle(shuffled);
            int wonCount = 0;
            List<DropTable.DropResult> wonByThisWinner = new ArrayList<DropTable.DropResult>();
            for (DropTable.DropEntry drop : shuffled) {
                if (wonCount >= maxItemsPerPerso) break;
                int jet = (int) (Math.random() * DropTable.MAX_RATE);
                if (jet < drop.rate) {
                    int qty = drop.qtyMin + (drop.qtyMax > drop.qtyMin
                            ? (int) (Math.random() * (drop.qtyMax - drop.qtyMin + 1))
                            : 0);
                    wonByThisWinner.add(new DropTable.DropResult(drop.templateId, qty));
                    // AncestraR : décrémenter le max d'occurences, retirer si épuisé.
                    drop.max--;
                    if (drop.max <= 0) possibleDrops.remove(drop);
                    wonCount++;
                }
            }
            if (!wonByThisWinner.isEmpty()) {
                rewards.dropsByFighter.put(winner.getId(), wonByThisWinner);
                rewards.allDrops.addAll(wonByThisWinner);
            }
        }

        // --- Étape 5 : pré-calcul pour formule XP officielle AncestraR (Formulas.getXpWinPvm2) ---
        int lvlMax = 1;
        int lvlWinnersSum = 0;
        for (Fighter w : winners) {
            Characters chr = WorldData.getCharacterById(w.getId());
            int lvl = chr != null ? chr.getExperience().getLevel() : w.getLevel();
            if (lvl > lvlMax) lvlMax = lvl;
            lvlWinnersSum += lvl;
        }
        int nbBonus = 0;
        for (Fighter w : winners) {
            Characters chr = WorldData.getCharacterById(w.getId());
            int lvl = chr != null ? chr.getExperience().getLevel() : w.getLevel();
            if (lvl > lvlMax / 3) nbBonus++;
        }
        // Table bonus AncestraR Formulas.getXpWinPvm2
        double bonus;
        switch (nbBonus) {
            case 0: case 1: bonus = 1.0; break;
            case 2: bonus = 1.1; break;
            case 3: bonus = 1.3; break;
            case 4: bonus = 2.2; break;
            case 5: bonus = 2.5; break;
            case 6: bonus = 2.8; break;
            case 7: bonus = 3.1; break;
            default: bonus = 3.5; break;
        }
        int lvlLoosersSum = 0;
        for (Fighter f : getTeam(1)) {
            lvlLoosersSum += f.getLevel();
        }
        double rapport1 = Math.max(1.3, 1.0 + (double) lvlLoosersSum / Math.max(1, lvlWinnersSum));
        int starBonus = monsterGroup != null ? monsterGroup.getStarBonus() : 0;

        // --- Étape 6 : kamas roll AncestraR Formulas.getKamasWin (roll [min, max], pas de partage) ---
        rewards.totalKamas = totalMinKamas + (totalMaxKamas > totalMinKamas
                ? (int) (Math.random() * (totalMaxKamas - totalMinKamas + 1))
                : 0);

        // --- Étape 7 : distribuer XP et kamas par winner ---
        for (Fighter winner : winners) {
            Characters chr = WorldData.getCharacterById(winner.getId());
            if (chr == null) continue;
            int winnerLevel = chr.getExperience().getLevel();
            int sage = Statistic.totalWithEquipment(chr, EConstants.ADD_WISDOM.getInt());
            double coef = (sage + 100.0) / 100.0;
            double rapport2 = 1.0 + (double) winnerLevel / Math.max(1, lvlWinnersSum);
            // Formule officielle : xpWin = groupXP × rapport1 × bonus × taux × coef × rapport2 × (1 + star/100)
            long xpShare = (long) (rewards.totalXp * rapport1 * bonus * coef * rapport2);
            if (starBonus > 0) xpShare = xpShare + xpShare * starBonus / 100L;

            // Kamas : chaque joueur reçoit son propre roll (formule officielle AncestraR)
            int kamasShare = (int) rewards.totalKamas;

            rewards.xpByFighter.put(winner.getId(), xpShare);
            rewards.kamasByFighter.put(winner.getId(), (long) kamasShare);

            if (xpShare > 0) chr.getExperience().add(xpShare);
            if (kamasShare > 0) chr.setKamas(chr.getKamas() + kamasShare);

            IoSession sess = sessionFor(winner);
            List<DropTable.DropResult> winnerDrops = rewards.dropsByFighter.get(winner.getId());
            if (winnerDrops != null && !winnerDrops.isEmpty()) {
                giveDrops(chr, sess, winnerDrops);
            }
            if (kamasShare > 0 && sess != null && sess.isConnected()) sess.write("Of+" + kamasShare);
            if (sess != null && sess.isConnected()) org.dofus.utils.RegenService.start(chr);
            CharactersData.update(chr);
        }

        return rewards;
    }

    private void giveDrops(Characters chr, IoSession sess, List<DropTable.DropResult> drops) {
        for (DropTable.DropResult drop : drops) {
            ItemTemplate tpl = ItemsData.getTemplate(drop.templateId);
            if (tpl == null) continue;
            Item stacked = findStackable(chr.getInventory(), tpl);
            Item item = chr.getInventory().addItem(tpl, drop.quantity);
            if (stacked != null && stacked.getUid() == item.getUid()) ItemsData.update(item);
            else ItemsData.insert(chr.getId(), item);
            if (sess != null && sess.isConnected()) sess.write(Inventory.buildOAPacket(item));
        }
        if (sess != null && sess.isConnected()) sess.write("Ow" + chr.getInventory().getUsedPods() + "|" + chr.getMaxPods());
    }

    /**
     * Restauration serveur-side d'un fighter à la fin du combat :
     *   - cellule roleplay d'origine (mémorisée à la création du Fighter)
     *   - vie au minimum 1 (pas 0 même mort)
     *   - persistance BDD
     *   - annonce GM|+player aux AUTRES acteurs de la map roleplay
     *
     * On N'ENVOIE PAS le map view au joueur lui-même ici : il a le panneau résultat
     * affiché par le GE et ne doit pas voir la map de fond avant d'avoir cliqué Fermer.
     * Le map view est envoyé via {@link org.dofus.network.game.handlers.parsers.GameParser#creation}
     * quand le client envoie GC1 après fermeture du panneau (séquence Ancestra exacte).
     */
    private void restorePlayersAfterFight() {
        for (Fighter f : fighters.values()) {
            if (f.getType() != Fighter.FighterType.PLAYER) continue;
            Characters chr = WorldData.getCharacterById(f.getId());
            if (chr == null) continue;
            short restoreCell = f.getOriginalCell() > 0 ? f.getOriginalCell() : f.getCell();
            chr.setCurrentCell(restoreCell);
            chr.setLife((short)Math.max(1, f.getCurrentLife()));
            if (f.isDead()) PetService.onOwnerDeath(chr, sessionFor(f));
            IoSession session = sessionFor(f);
            if (session != null && session.isConnected()) {
                map.addActor(chr);
                // Annonce uniquement aux AUTRES acteurs présents sur la map (le joueur
                // lui-même reçoit son map view via GameParser.creation sur GC1).
                StringBuilder selfEntry = new StringBuilder("GM|+");
                org.dofus.network.game.protocols.GProtocol.getCharacterPattern(selfEntry, chr);
                String selfPacket = selfEntry.toString();
                for (Characters other : new ArrayList<Characters>(map.getActors().values())) {
                    if (other == null || other == chr) continue;
                    IoSession otherSess = WorldData.getSessionByAccount().get(other.getOwner());
                    if (otherSess != null && otherSess.isConnected()) otherSess.write(selfPacket);
                }
            }
            CharactersData.update(chr);
        }
    }

    public void handleDisconnect(int characterId) {
        Fighter fighter = fighters.get(characterId);
        if (fighter == null || state == State.FINISHED) return;
        fighter.setDisconnected(true);
        fighter.setReady(false);
        broadcast("GA;950;" + characterId + ";" + characterId + ",1");
        if (turn.getCurrentFighter() != null && turn.getCurrentFighter().getId() == characterId) {
            turn.endTurn();
        }
    }

    public void reconnect(Characters character, IoSession session) {
        Fighter fighter = fighters.get(character.getId());
        if (fighter == null || state == State.FINISHED || session == null) return;

        fighter.setDisconnected(false);
        character.setCurrentCell(fighter.getCell());

        sendJoinPacket(session, state == State.PLACEMENT, false);
        if (state == State.PLACEMENT) {
            session.write("GP" + team0Places + "|" + team1Places + "|" + fighter.getTeamId());
        }
        sendFightSprites(session);
        session.write(buildCoordinatesPacket());

        if (state == State.ACTIVE) {
            session.write("GDK");
            session.write("GS");
            session.write("GTL|" + buildTurnList());
            session.write(buildTurnStatusPacket());
            if (turn.getCurrentFighter() != null) {
                session.write("GTS" + turn.getCurrentFighter().getId() + "|" + turn.getRemainingMs());
            }
        }

        broadcast("GA;950;" + character.getId() + ";" + character.getId() + ",0");
    }

    public void addSpectator(Characters character, IoSession session) {
        if (session == null || state == State.FINISHED) return;
        if (!spectators.contains(session)) spectators.add(session);
        sendJoinPacket(session, state == State.PLACEMENT, true);
        if (state == State.PLACEMENT) {
            session.write("GP" + team0Places + "|" + team1Places + "|0");
        }
        sendFightSprites(session);
        session.write(buildCoordinatesPacket());
        if (state == State.ACTIVE) {
            session.write("GDK");
            session.write("GS");
            session.write("GTL|" + buildTurnList());
            session.write(buildTurnStatusPacket());
            if (turn.getCurrentFighter() != null) session.write("GTS" + turn.getCurrentFighter().getId() + "|" + turn.getRemainingMs());
        }
    }

    public boolean isSpectator(IoSession session) {
        if (session == null) return false;
        synchronized (spectators) {
            return spectators.contains(session);
        }
    }

    public boolean removeSpectator(IoSession session) {
        if (session == null) return false;
        synchronized (spectators) {
            return spectators.remove(session);
        }
    }

    public void broadcast(String packet) {
        for (Fighter f : new ArrayList<Fighter>(fighters.values())) {
            IoSession session = sessionFor(f);
            if (session != null && session.isConnected()) session.write(packet);
        }
        synchronized (spectators) {
            spectators.removeIf(s -> s == null || !s.isConnected());
            for (IoSession spectator : spectators) spectator.write(packet);
        }
    }

    public void broadcastTurnState() {
        if (state == State.ACTIVE) broadcast(buildTurnStatusPacket());
    }

    void broadcastFightSprites() {
        String packet = buildAllSpritesGMPacket();
        if (packet == null) return;
        broadcast(packet);
    }

    private void sendFightSprites(IoSession session) {
        if (session == null || !session.isConnected()) return;
        String packet = buildAllSpritesGMPacket();
        if (packet != null) session.write(packet);
    }

    private String buildAllSpritesGMPacket() {
        StringBuilder sb = new StringBuilder("GM");
        boolean any = false;
        for (Fighter fighter : fighters.values()) {
            StringBuilder entry = new StringBuilder();
            appendGMEntry(entry, fighter);
            if (entry.length() == 0) continue;
            sb.append("|+").append(entry);
            any = true;
        }
        return any ? sb.toString() : null;
    }

    private String buildSingleGMPacket(Fighter fighter) {
        StringBuilder entry = new StringBuilder();
        appendGMEntry(entry, fighter);
        if (entry.length() == 0) return null;
        return "GM|+" + entry.toString();
    }

    private void scheduleFightSpritesRefresh() {
        FightTurn.schedule(new Runnable() {
            public void run() {
                if (Fight.this.getState() == State.FINISHED) return;
                broadcastFightSprites();
                broadcast(buildCoordinatesPacket());
                if (Fight.this.getState() == State.ACTIVE) broadcast(buildTurnStatusPacket());
            }
        }, FIGHT_SPRITE_REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendFightStateToFighters(boolean placement) {
        for (Fighter f : fighters.values()) {
            if (f.getType() != Fighter.FighterType.PLAYER) continue;
            IoSession session = sessionFor(f);
            if (session != null && session.isConnected()) sendJoinPacket(session, placement, false);
        }
    }

    private void sendJoinPacket(IoSession session, boolean placement, boolean spectator) {
        int cancelButton = 0;
        int duelFlag = spectator ? 0 : 1;
        int spectatorFlag = spectator ? 1 : 0;
        int timer = placement ? PLACEMENT_DURATION_SEC * 1000 : 0;
        session.write("GJK2|" + cancelButton + "|" + duelFlag + "|" + spectatorFlag + "|" + timer + "|" + FIGHT_TYPE_PVM);
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
        for (Fighter f : team) {
            sb.append(';').append(resultNameData(f)).append('~').append(f.getLevel());
        }
    }

    private void appendGMEntry(StringBuilder sb, Fighter fighter) {
        if (fighter.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(fighter.getId());
            if (chr == null) return;
            // Format StarLoco PlayerFighter.getGMPacketParts (officiel Dofus 1.29 fight) :
            // cell;dir;0;id;name;classe;gfx^size;sex;level;align,0,grade,levelPlusId;
            //   col1;col2;col3;accessories;currentPdv;baseAP;baseMP;resN;resE;resF;resW;resA;dodgePA;dodgePM;team
            String accessories = chr.getInventory() != null ? chr.getInventory().buildAccessories(true) : "";
            int levelPlusId = chr.getExperience().getLevel() + fighter.getId();
            sb.append(fighter.getCell()).append(';')
              .append("1;0;")
              .append(fighter.getId()).append(';')
              .append(chr.getName()).append(';')
              .append(chr.getBreed().getId()).append(';')
              .append(chr.getSkin()).append('^').append(chr.getSize()).append(';')
              .append(chr.getGender()).append(';')
              .append(chr.getExperience().getLevel()).append(';')
              .append(chr.getAlignmentType()).append(",0,0,")
              .append(levelPlusId).append(';')
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
              .append(fighter.getTeamId());
            return;
        }

        // Format AncestraR Fighter.getGmPacket case 2 (Mob) — identique mot pour mot :
        //   cell;1;0;guid;templateId;-2;gfx^100;grade;c1;c2;c3;0,0,0,0;maxPdv;PA;PM;team
        // - dir HARDCODÉ à "1" (la référence force _orientation = 1)
        // - field 8 = grade index (1-5), PAS le level réel
        // - colors = _mob.getTemplate().getColors().replace(",", ";") → "-1;-1;-1"
        // - PAS de résistances séparées : juste maxPdv;PA;PM;team
        // - PAS de trailing ; après team (sinon le client lit un champ vide à la fin)
        int gfx = fighter.getGfxId() > 0 ? fighter.getGfxId() : 31;
        int gradeNum = fighter.getMobGrade() > 0 ? fighter.getMobGrade() : 1;
        sb.append(fighter.getCell()).append(';')
          .append("1;0;")
          .append(fighter.getId()).append(';')
          .append(resultNameData(fighter)).append(';')
          .append("-2;")
          .append(gfx).append("^100;") //FIXME: ^100 c'est la taille du monstre normalement il y a un formule avec le level du monstre pour déterminer ca taille
          .append(gradeNum).append(';')
          .append("-1;-1;-1;")
          .append("0,0,0,0;")
          .append(fighter.getMaxLife()).append(';')
          .append(fighter.getBaseAP()).append(';')
          .append(fighter.getBaseMP()).append(';')
          .append(fighter.getTeamId());
    }

    String buildCoordinatesPacket() {
        StringBuilder sb = new StringBuilder("GIC");
        for (Fighter f : fighters.values()) {
            sb.append('|').append(f.getId()).append(';').append(f.getCell());
        }
        return sb.toString();
    }

    private String buildTurnList() {
        StringBuilder sb = new StringBuilder();
        for (Fighter f : turnOrder) {
            if (sb.length() > 0) sb.append('|');
            sb.append(f.getId());
        }
        return sb.toString();
    }


    String buildTurnStatusPacket() {
        StringBuilder sb = new StringBuilder("GTM");
        for (Fighter f : fighters.values()) {
            sb.append('|').append(f.getId()).append(';');
            if (f.isDead()) {
                sb.append('1');
                continue;
            }
            sb.append("0;")
              .append(f.getCurrentLife()).append(';')
              .append(f.getCurrentAP()).append(';')
              .append(f.getCurrentMP()).append(';')
              .append(f.getCell()).append(";;")
              .append(f.getMaxLife());
        }
        return sb.toString();
    }

    public boolean isCellValid(short cell) {
        return map != null && map.isValidCellId(cell);
    }

    public boolean isCellFreeFor(Fighter fighter, short cell) {
        return isCellValid(cell) && !isOccupiedByOther(fighter, cell);
    }

    public Fighter findNearestEnemy(Fighter source) {
        Fighter best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Fighter f : fighters.values()) {
            if (f == null || f.isDead() || f.getTeamId() == source.getTeamId()) continue;
            int distance = Math.abs(f.getCell() - source.getCell());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = f;
            }
        }
        return best;
    }

    public void syncFightState() {
        broadcast(buildCoordinatesPacket());
        broadcast(buildTurnStatusPacket());
    }

    public void sendMovementEnd(Fighter fighter, int steps) {
        if (fighter == null || fighter.isDead()) return;
        // Format AncestraR : GA;129;<id>;<id>,-N (PAS de gaId, pas de GIC/GTM ici).
        // GTM/GTR sont diffusés par FightTurn.endTurn() après le tour.
        if (steps > 0) broadcast("GA;129;" + fighter.getId() + ";" + fighter.getId() + ",-" + steps);
    }


    /**
     * Construit le packet {@code GE} (fenêtre résultat de fin de combat).
     *
     * Format AncestraR PvM (Fight.GetGE) :
     * <pre>
     *   GE&lt;time&gt;;&lt;starBonus&gt;|&lt;initiatorGUID&gt;|&lt;fightType&gt;|&lt;entry1&gt;|&lt;entry2&gt;|...
     * </pre>
     *
     * Chaque entrée a un préfixe selon le rôle :
     * <ul>
     *   <li>{@code 2;...} — gagnant (player)</li>
     *   <li>{@code 0;...} — perdant (player ou mob)</li>
     * </ul>
     *
     * Les gagnants sont listés triés par prospection descendante (cohérent
     * avec l'algorithme de distribution des drops).
     */
    private String buildResultPacket(int winnerTeam, RewardContext rewards) {
        // AncestraR : <time> = durée de la phase ACTIVE seule (sans placement).
        long fightStart = startedAt > 0L ? startedAt : createdAt;
        long time       = Math.max(0L, System.currentTimeMillis() - fightStart);
        int  initiator  = firstPlayerId();
        int  starBonus  = monsterGroup != null ? monsterGroup.getStarBonus() : 0;

        StringBuilder sb = new StringBuilder("GE");
        sb.append(time).append(';').append(starBonus);
        // AncestraR : type 0 (CHALLENGE) pour PvM, pas 4 (FIGHT_TYPE_PVM) — voir Fight.GetGE.
        sb.append('|').append(initiator).append('|').append(FIGHT_GE_TYPE_CHALLENGE).append('|');

        // Tri winners par prospection desc — même ordre que la distribution
        // des drops dans calculateAndApplyRewards (le + prospecteur loot en 1er).
        List<Fighter> sortedWinners = new ArrayList<>(getTeam(winnerTeam));
        sortedWinners.sort((a, b) -> Integer.compare(prospectionOf(b), prospectionOf(a)));

        for (Fighter f : sortedWinners) {
            sb.append(buildWinnerEntry(f, rewards)).append('|');
        }
        for (Fighter f : getTeam(1 - winnerTeam)) {
            sb.append(buildLoserEntry(f)).append('|');
        }

        // AncestraR garde le | trailing comme délimiteur de fin d'entrée — ne pas le retirer,
        // sinon le client peut sauter la dernière entrée lors du parsing.
        return sb.toString();
    }

    /**
     * Entrée d'un joueur gagnant :
     * {@code 2;<guid>;<name>;<lvl>;<dead>;<min>;<cur>;<max>;<winXp>;<guildXp>;<mountXp>;<drops>;<kamas>}.
     * Les champs nuls sont laissés vides (""), conformément à AncestraR.
     */
    private String buildWinnerEntry(Fighter f, RewardContext rewards) {
        long min   = 0L, cur = 0L, max = 0L;
        int  level = f.getLevel();
        String name = resultNameData(f);

        if (f.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if (chr != null && chr.getExperience() != null) {
                min   = chr.getExperience().min();
                cur   = chr.getExperience().getExperience();
                max   = chr.getExperience().max();
                level = chr.getExperience().getLevel();
                name  = chr.getName();
            }
        }

        long winXp = rewards.xpByFighter.getOrDefault(f.getId(), 0L);
        long kamas = rewards.kamasByFighter.getOrDefault(f.getId(), 0L);
        String drops = buildDropList(rewards.dropsByFighter.get(f.getId()));

        // Format AncestraR PvM winner (12 champs après le "2") :
        //   2;guid;name;level;dead;min;cur;max;winXp;guildXp;mountXp;drops;kamas
        // Mobs gagnants : champs XP/drops/kamas vides — le client affiche juste leur ligne.
        // guildXp et mountXp non implémentés → laissés vides.
        return "2;" + f.getId() + ";" + name + ";" + level + ";"
             + (f.isDead() ? 1 : 0) + ";"
             + min + ";" + cur + ";" + max + ";"
             + emptyIfZero(winXp) + ";;;"
             + drops + ";"
             + emptyIfZero(kamas);
    }

    /**
     * Entrée d'un perdant (joueur ou mob) :
     * {@code 0;<guid>;<name>;<lvl>;<dead>;<min>;<cur>;<max>;;;;}.
     * Les 4 derniers champs (winXp/guildXp/mountXp/drops) sont vides : un perdant ne gagne rien.
     */
    private String buildLoserEntry(Fighter f) {
        long min   = 0L, cur = 0L, max = 0L;
        int  level = f.getLevel();
        String name = resultNameData(f);

        if (f.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if (chr != null && chr.getExperience() != null) {
                min   = chr.getExperience().min();
                cur   = chr.getExperience().getExperience();
                max   = chr.getExperience().max();
                level = chr.getExperience().getLevel();
                name  = chr.getName();
            }
        }

        int deadFlag = (f.isDead() || f.isDisconnected()) ? 1 : 0;
        return "0;" + f.getId() + ";" + name + ";" + level + ";"
             + deadFlag + ";"
             + min + ";" + cur + ";" + max + ";;;;";
    }

    /** Renvoie {@code ""} si la valeur est 0 (convention AncestraR : champ vide), sinon la valeur. */
    private static String emptyIfZero(long v) { return v == 0L ? "" : String.valueOf(v); }

    /** Prospection effective d'un fighter (équipement + bonus chance). */
    private int prospectionOf(Fighter f) {
        if (f.getType() != Fighter.FighterType.PLAYER) return 0;
        Characters chr = WorldData.getCharacterById(f.getId());
        if (chr == null) return f.getChance() / 10;
        return Statistic.totalWithEquipment(chr, EConstants.ADD_PROSPECTION.getInt())
             + f.getChance() / 10;
    }

    private String buildDropList(List<DropTable.DropResult> drops) {
        if (drops == null || drops.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DropTable.DropResult drop : drops) {
            if (sb.length() > 0) sb.append(',');
            sb.append(drop.templateId).append('~').append(drop.quantity);
        }
        return sb.toString();
    }

    private void preparePlacementCells() {
        String places = map != null ? map.getPlaces() : null;
        String[] sides = places != null ? places.split("\\|", -1) : new String[0];
        if (sides.length >= 2) {
            team0Places = sides[0];
            team1Places = sides[1];
            decodePlacementSide(team0Places, team0Cells);
            decodePlacementSide(team1Places, team1Cells);
        }
    }

    public void ensurePlacementFallbacks(short attackerCell, short defenderCell) {
        if (team0Cells.isEmpty()) {
            List<Short> cells = fallbackCells(attackerCell);
            team0Places = encodeCells(cells);
            team0Cells.addAll(cells);
        }
        if (team1Cells.isEmpty()) {
            List<Short> cells = fallbackCells(defenderCell);
            team1Places = encodeCells(cells);
            team1Cells.addAll(cells);
        }
    }

    private List<Short> fallbackCells(short origin) {
        List<Short> cells = new ArrayList<>();
        int[] offsets = new int[] { 0, 1, -1, 14, -14, 15, -15, 28, -28 };
        for (int offset : offsets) {
            short cell = (short)(origin + offset);
            if (map.isValidCellId(cell) && !cells.contains(cell)) cells.add(cell);
            if (cells.size() >= 8) break;
        }
        if (cells.isEmpty()) cells.add(origin);
        return cells;
    }

    private void decodePlacementSide(String encoded, Set<Short> out) {
        if (encoded == null) return;
        for (Short cell : MapCellDecoder.decodePlacementCells(encoded)) out.add(cell);
    }

    private String encodeCells(List<Short> cells) {
        StringBuilder sb = new StringBuilder();
        for (Short cell : cells) {
            if (cell != null) sb.append(encodeCellBase64(cell.shortValue()));
        }
        return sb.toString();
    }

    private boolean isAllowedPlacementCell(Fighter fighter, short cell) {
        Set<Short> allowed = fighter.getTeamId() == 0 ? team0Cells : team1Cells;
        return allowed.isEmpty() || allowed.contains(cell);
    }

    private boolean isOccupiedByOther(Fighter fighter, short cell) {
        for (Fighter f : fighters.values()) {
            if (f.getId() != fighter.getId() && !f.isDead() && f.getCell() == cell) return true;
        }
        return false;
    }

    private boolean allPlayableFightersReady() {
        for (Fighter f : fighters.values()) {
            if (f.getType() == Fighter.FighterType.MONSTER) continue;
            if (!f.isDead() && !f.isReady()) return false;
        }
        return true;
    }

    private int findWinnerTeam() {
        for (Fighter f : fighters.values()) {
            if (!f.isDead()) return f.getTeamId();
        }
        return -1;
    }

    private List<Fighter> alivePlayers(List<Fighter> team) {
        List<Fighter> result = new ArrayList<>();
        for (Fighter f : team) {
            if (f.getType() == Fighter.FighterType.PLAYER && !f.isDead()) result.add(f);
        }
        return result;
    }

    private int calculateTeamProspection(int teamId) {
        int total = 0;
        for (Fighter f : getTeam(teamId)) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if (chr != null) {
                total += Statistic.totalWithEquipment(chr, EConstants.ADD_PROSPECTION.getInt());
                total += f.getChance() / 10;
            }
        }
        return Math.max(100, total);
    }

    private Fighter findFighterOnCell(short cell) {
        for (Fighter f : fighters.values()) {
            if (!f.isDead() && f.getCell() == cell) return f;
        }
        return null;
    }

    private int getKnownSpellLevel(Fighter fighter, int spellId) {
        if (fighter.getType() != Fighter.FighterType.PLAYER) return 1;
        Characters character = WorldData.getCharacterById(fighter.getId());
        if (character == null) return 0;
        KnownSpell spell = character.getKnownSpell(spellId);
        return spell == null ? 0 : spell.getLevel();
    }

    private int getStatForElement(Fighter f, int element) {
        switch (element) {
            case 1: return f.getStrength();
            case 2: return f.getChance();
            case 3: return f.getIntel();
            case 4: return f.getAgility();
            default: return f.getStrength();
        }
    }

    private int effectIdToElement(int effectId) {
        switch (effectId) {
            case 91: case 96: return 2;
            case 92: case 97: return 1;
            case 93: case 98: return 4;
            case 94: case 99: return 3;
            case 95: case 100: return 0;
            default: return 0;
        }
    }

    private short parseCellArg(String args) {
        if (args == null) throw new IllegalArgumentException("missing cell");
        String value = args;
        if (value.contains("|")) value = value.substring(value.lastIndexOf('|') + 1);
        if (value.contains(";")) value = value.substring(value.lastIndexOf(';') + 1);
        value = value.trim();
        if (value.length() == 2 && !isInteger(value)) return decodeCellBase64(value);
        return Short.parseShort(value);
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static short decodeCellBase64(String s) {
        if (s == null || s.length() < 2) throw new IllegalArgumentException("cell too short");
        int high = StringUtils.HASH.indexOf(s.charAt(0));
        int low = StringUtils.HASH.indexOf(s.charAt(1));
        if (high < 0 || low < 0) throw new IllegalArgumentException("bad cell encoding");
        return (short)(high * 64 + low);
    }

    static String encodeCellBase64(short cellId) {
        return String.valueOf(StringUtils.HASH.charAt(cellId / 64)) + StringUtils.HASH.charAt(cellId % 64);
    }

    private IoSession sessionFor(Fighter fighter) {
        if (fighter == null || fighter.getType() != Fighter.FighterType.PLAYER) return null;
        Characters chr = WorldData.getCharacterById(fighter.getId());
        if (chr == null) return null;
        return WorldData.getSessionByAccount().get(chr.getOwner());
    }

    private int firstPlayerId() {
        for (Fighter f : fighters.values()) {
            if (f.getType() == Fighter.FighterType.PLAYER) return f.getId();
        }
        return 0;
    }

    private String resultNameData(Fighter f) {
        if (f.getType() == Fighter.FighterType.PLAYER) {
            Characters chr = WorldData.getCharacterById(f.getId());
            return chr != null ? chr.getName() : f.getName();
        }
        if (f.getTemplateId() > 0) return String.valueOf(f.getTemplateId());
        if (f.getId() >= 1000) return String.valueOf(f.getId() / 1000);
        return f.getName();
    }

    private void cancelPlacementTimer() {
        if (placementTimer != null && !placementTimer.isDone()) placementTimer.cancel(false);
        placementTimer = null;
    }

    private static Item findStackable(Inventory inventory, ItemTemplate template) {
        if (template.getTypeId() < 48) return null;
        for (Item item : inventory.getBag()) {
            if (item.getTemplate().getId() == template.getId()) return item;
        }
        return null;
    }

    public int getId() { return id; }
    public MapTemplate getMap() { return map; }
    public State getState() { return state; }
    public FightTurn getTurn() { return turn; }

    public static Fight getFight(int fightId) {
        return activeFights.get(fightId);
    }

    public static Map<Integer, Fight> getActiveFights() {
        return Collections.unmodifiableMap(activeFights);
    }

    private static final class RewardContext {
        long totalXp = 0;
        long totalKamas = 0;
        final List<DropTable.DropResult> allDrops = new ArrayList<>();
        final Map<Integer, Long> xpByFighter = new LinkedHashMap<>();
        final Map<Integer, Long> kamasByFighter = new LinkedHashMap<>();
        final Map<Integer, List<DropTable.DropResult>> dropsByFighter = new LinkedHashMap<>();

        int totalDropCount() {
            int count = 0;
            for (List<DropTable.DropResult> drops : dropsByFighter.values()) count += drops.size();
            return count;
        }
    }
}
