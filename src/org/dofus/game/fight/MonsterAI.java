package org.dofus.game.fight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.dofus.objects.actors.EOrientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IA PvM volontairement simple mais synchronisée avec le client 1.29.
 *
 * Point important : l'ancien code enchaînait déplacement, retrait PM, GTM et fin
 * de tour dans la même milliseconde. Le client recevait donc plusieurs tours avant
 * la fin de l'animation et les monstres semblaient se téléporter ou courir sur place.
 * Cette version temporise chaque action avant de finir le tour.
 */
public final class MonsterAI {
    private static final Logger logger = LoggerFactory.getLogger(MonsterAI.class);

    private static final int MELEE_AP_COST = 3;
    private static final int MAP_WIDTH = 14;
    private static final long AI_THINK_DELAY_MS = 250L;
    // Calé sur le délai mesuré client (~650-700ms/case) — voir packets.log roleplay
    // intervalle GA1;1 → GKK1. Trop court = sprite TP au milieu de l'animation.
    private static final long MOVE_STEP_DURATION_MS = 700L;
    private static final long MOVE_END_PADDING_MS = 500L;
    private static final long ATTACK_ANIMATION_MS = 900L;
    private static final long EMPTY_TURN_DELAY_MS = 450L;

    private MonsterAI() {
    }

    public static void playTurn(final Fight fight, final Fighter monster) {
        if (fight == null || monster == null || monster.isDead()) {
            safeEndTurn(fight);
            return;
        }

        FightTurn.schedule(new Runnable() {
            public void run() {
                playTurnNow(fight, monster);
            }
        }, AI_THINK_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void playTurnNow(final Fight fight, final Fighter monster) {
        if (fight.getState() != Fight.State.ACTIVE || fight.getTurn().getCurrentFighter() == null || fight.getTurn().getCurrentFighter().getId() != monster.getId()) {
            return;
        }

        final Fighter target = fight.findNearestEnemy(monster);
        if (target == null || target.isDead()) {
            safeEndTurn(fight);
            return;
        }

        if (!isAdjacent(monster.getCell(), target.getCell()) && monster.getCurrentMP() > 0) {
            MonsterMove move = buildMoveToward(fight, monster, target, monster.getCurrentMP());
            if (move.steps > 0) {
                executeMoveThenMaybeAttack(fight, monster, target, move);
                return;
            }
        }

        if (canMelee(monster, target)) {
            executeMeleeThenEnd(fight, monster, target);
            return;
        }

        FightTurn.schedule(new Runnable() {
            public void run() {
                safeEndTurn(fight);
            }
        }, EMPTY_TURN_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void executeMoveThenMaybeAttack(final Fight fight, final Fighter monster, final Fighter target, final MonsterMove move) {
        if (!monster.spendMP(move.steps)) {
            safeEndTurn(fight);
            return;
        }

        // Animation : "a" + cellule de départ encodée + path.
        // AncestraR ne diffuse PAS de GAS pour les mobs (commenté : "les monstres n'ont
        // pas de GAS/GAF"). L'avoir ajouté cassait l'animation côté client 1.29.1.
        short fromCell = monster.getCell();
        String animPath = "a" + Fight.encodeCellBase64(fromCell) + move.encodedPath;
        monster.setCell(move.destination);
        // Met à jour l'orientation du mob avec la direction du dernier step — sinon il
        // garde l'orientation de placement initial (SOUTH) tour après tour, et son sprite
        // se déplace "en crabe" au lieu de marcher dans la direction du mouvement.
        EOrientation finalOrient = EOrientation.valueOf(
            org.dofus.utils.StringUtils.HASH.indexOf(move.finalDirChar));
        if (finalOrient != null) monster.setOrientation(finalOrient);
        fight.broadcast("GA" + Fight.nextGaPacketId() + ";1;" + monster.getId() + ";" + animPath);

        long moveDuration = Math.max(MOVE_END_PADDING_MS, MOVE_END_PADDING_MS + (MOVE_STEP_DURATION_MS * move.steps));
        FightTurn.schedule(new Runnable() {
            public void run() {
                if (fight.getState() != Fight.State.ACTIVE || monster.isDead()) return;
                fight.sendMovementEnd(monster, move.steps);

                if (canMelee(monster, target)) {
                    executeMeleeThenEnd(fight, monster, target);
                } else {
                    safeEndTurn(fight);
                }
            }
        }, moveDuration, TimeUnit.MILLISECONDS);
    }

    private static void executeMeleeThenEnd(final Fight fight, final Fighter monster, final Fighter target) {
        if (monster.isDead() || target == null || target.isDead() || !monster.spendAP(MELEE_AP_COST)) {
            safeEndTurn(fight);
            return;
        }

        int rawDamage = Math.max(1, monster.getStrength() / 10 + 1);
        int dealt = target.takeDamage(rawDamage, 0);

        // Format AncestraR : follow-up actions sans gaId (GA;<type>;...) — client n'a rien à ACK.
        // PAS de GAS pour les mobs (Ancestra commente "les monstres n'ont pas de GAS/GAF").
        // Deltas PA/PV NÉGATIFS : client fait currentX += delta.
        // PAS de syncFightState ici — le GTM final vient via FightTurn.endTurn().
        fight.broadcast("GA;303;" + monster.getId() + ";" + target.getId());
        fight.broadcast("GA;102;" + monster.getId() + ";" + monster.getId() + ",-" + MELEE_AP_COST);
        fight.broadcast("GA;100;" + monster.getId() + ";" + target.getId() + ",-" + dealt);

        if (target.isDead()) {
            fight.broadcast("GA;103;" + monster.getId() + ";" + target.getId());
        }

        FightTurn.schedule(new Runnable() {
            public void run() {
                safeEndTurn(fight);
            }
        }, ATTACK_ANIMATION_MS, TimeUnit.MILLISECONDS);
    }

    private static boolean canMelee(Fighter monster, Fighter target) {
        return monster != null && target != null && !monster.isDead() && !target.isDead()
                && monster.getCurrentAP() >= MELEE_AP_COST
                && isAdjacent(monster.getCell(), target.getCell());
    }

    private static MonsterMove buildMoveToward(Fight fight, Fighter monster, Fighter target, int maxSteps) {
        List<Short> cells = new ArrayList<Short>();
        int current = monster.getCell();
        int targetCell = target.getCell();

        for (int step = 0; step < maxSteps; step++) {
            short next = nextCellToward((short) current, (short) targetCell);
            if (next == current) break;
            if (isAdjacent(next, target.getCell())) {
                if (fight.isCellFreeFor(monster, next)) {
                    cells.add(next);
                    current = next;
                }
                break;
            }
            if (!fight.isCellFreeFor(monster, next)) break;
            cells.add(next);
            current = next;
        }

        if (cells.isEmpty()) return MonsterMove.empty(monster.getCell());

        StringBuilder path = new StringBuilder();
        char lastDir = 'c';
        short from = monster.getCell();
        for (Short cell : cells) {
            if (cell == null) continue;
            char dir = directionChar(from, cell.shortValue());
            lastDir = dir;
            path.append(dir);
            path.append(Fight.encodeCellBase64(cell.shortValue()));
            from = cell.shortValue();
        }
        return new MonsterMove(path.toString(), cells.get(cells.size() - 1).shortValue(),
                cells.size(), lastDir);
    }

    private static short nextCellToward(short from, short to) {
        int dx = (to % MAP_WIDTH) - (from % MAP_WIDTH);
        int dy = (to / MAP_WIDTH) - (from / MAP_WIDTH);
        int delta;
        if (Math.abs(dx) >= Math.abs(dy)) {
            delta = dx > 0 ? 1 : -1;
        } else {
            delta = dy > 0 ? MAP_WIDTH : -MAP_WIDTH;
        }
        return (short) (from + delta);
    }

    /**
     * Mapping Dofus 1.29 isométrique 14-wide : delta cellId → char de direction.
     *
     * Reverse-engineering empirique à partir des paths joueur capturés
     * (le client envoie des dir chars cohérents avec sa propre logique iso) :
     * <pre>
     *   Player path `adihcS` : delta = 172-200 = -28 = 2 × -14 avec dir 'h'
     *      → delta -14 utilise 'h' (NORD-EST en visuel iso)
     *   Player path `acSddibdx` : step1 delta = 200-172 = +28 = 2 × +14 avec dir 'd'
     *      → delta +14 utilise 'd' (SUD-OUEST en visuel iso)
     *   Player path `beedes` : step1 delta = 260-215 = +45 = 3 × +15 avec dir 'b'
     *      → delta +15 utilise 'b' (SUD-EST en visuel iso)
     * </pre>
     *
     * NB : en iso Dofus, +1 (même row, col+1) et +14 (next row, même col) sont
     * visuellement la MÊME direction (DOWN-LEFT) — d'où le même char 'd' pour les deux.
     */
    private static char directionChar(short from, short to) {
        int diff = to - from;
        switch (diff) {
            case 1:                  return 'd';
            case -1:                 return 'h';
            case MAP_WIDTH:          return 'd';  // +14 → SUD-OUEST visuel (idem +1)
            case -MAP_WIDTH:         return 'h';  // -14 → NORD-EST visuel (idem -1)
            case MAP_WIDTH + 1:      return 'b';  // +15 → SUD-EST visuel
            case -(MAP_WIDTH + 1):   return 'f';  // -15 → NORD-OUEST visuel
            case MAP_WIDTH - 1:      return 'f';  // +13 → guess, mirror
            case -(MAP_WIDTH - 1):   return 'b';  // -13 → guess
            default:                 return 'd';  // fallback SUD-OUEST
        }
    }

    private static boolean isAdjacent(short a, short b) {
        int diff = Math.abs(a - b);
        return diff == 1 || diff == MAP_WIDTH;
    }

    private static void safeEndTurn(Fight fight) {
        if (fight == null || fight.getState() != Fight.State.ACTIVE) return;
        try {
            fight.getTurn().endTurn();
        } catch (Throwable t) {
            logger.error("MonsterAI : erreur pendant la fin de tour", t);
        }
    }

    private static final class MonsterMove {
        final String encodedPath;
        final short destination;
        final int steps;
        /** Direction (char Dofus 1.29) du dernier step, pour orienter le sprite à l'arrivée. */
        final char finalDirChar;

        MonsterMove(String encodedPath, short destination, int steps, char finalDirChar) {
            this.encodedPath = encodedPath;
            this.destination = destination;
            this.steps = steps;
            this.finalDirChar = finalDirChar;
        }

        static MonsterMove empty(short cell) {
            return new MonsterMove("", cell, 0, 'c');
        }
    }
}
