package org.dofus.game.fight;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IA simplifiée pour les monstres pendant un combat Dofus 1.29.
 *
 * Comportement :
 *   1. Si un ennemi est adjacent → attaque (dégâts de base × force).
 *   2. Sinon → se déplace vers l'ennemi le plus proche (PM disponibles).
 *   3. Si encore des PA restants après déplacement → attaque si portée.
 *   4. Fin de tour.
 *
 * Formule de dégâts de base (sans sort) :
 *   damage = max(1, floor(strength / 10) + 1) — attaque au corps-à-corps.
 *
 * L'IA termine toujours le tour après ses actions.
 */
public class MonsterAI {

    private static final Logger logger = LoggerFactory.getLogger(MonsterAI.class);

    /** PA minimum conservés pour attaquer après déplacement. */
    private static final int MIN_AP_TO_ATTACK = 3;

    /**
     * Exécute le tour d'un fighter monstre.
     * Appelé par {@link FightTurn#startTurn(Fighter)} quand le fighter est un monstre.
     *
     * @param fight   Le combat en cours
     * @param monster Le fighter monstre qui joue
     */
    public static void playTurn(Fight fight, Fighter monster) {
        logger.debug("MonsterAI : tour du monstre {} (AP={} MP={})",
        		new Object[] { monster.getName(), monster.getCurrentAP(), monster.getCurrentMP()});

        List<Fighter> enemies = getEnemies(fight, monster);
        if(enemies.isEmpty()) {
            fight.getTurn().endTurn();
            return;
        }

        Fighter target = findClosestEnemy(monster, enemies);

        // Déplacement vers la cible si hors portée adjacente
        if(!isAdjacent(monster.getCell(), target.getCell()) && monster.getCurrentMP() > 0) {
            moveToward(fight, monster, target);
        }

        // Attaque si adjacent et PA suffisants
        if(monster.getCurrentAP() >= MIN_AP_TO_ATTACK && isAdjacent(monster.getCell(), target.getCell())) {
            meleeAttack(fight, monster, target);
        }

        // Fin du tour monstre
        fight.getTurn().endTurn();
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private static void meleeAttack(Fight fight, Fighter monster, Fighter target) {
        if(!monster.spendAP(MIN_AP_TO_ATTACK)) return;

        // Dégâts de base : force/10 + 1, élément neutre
        int rawDamage = Math.max(1, monster.getStrength() / 10 + 1);
        int dealt     = target.takeDamage(rawDamage, 0);

        // Paquet GA 303 = attaque corps-à-corps / dégâts
        fight.broadcast("GA303;" + monster.getId() + ";" + target.getId() + ";" + dealt);
        fight.broadcast("GA306;" + monster.getId() + ";" + target.getId() + ";" + target.getCurrentLife());

        logger.debug("MonsterAI : {} frappe {} pour {} dégâts (vie={}/{})",
        	new Object[] { monster.getName(), target.getName(), dealt, target.getCurrentLife(), target.getMaxLife()});

        if(target.isDead()) {
            fight.broadcast("GA402;" + target.getId() + ";0"); // mort
            logger.debug("MonsterAI : {} est mort", target.getName());
        }
    }

    private static void moveToward(Fight fight, Fighter monster, Fighter target) {
        // Calcul de déplacement simplifié sur grille isométrique
        // La map Dofus est une grille de 33 colonnes (largeur map standard)
        int    mapWidth = 14; // demi-largeur standard
        short  from     = monster.getCell();
        short  to       = target.getCell();
        int    mp       = monster.getCurrentMP();

        // Calcule le chemin direct cellule par cellule
        List<Short> path = buildPath(from, to, mapWidth, mp);
        if(path.isEmpty()) return;

        short dest = path.get(path.size() - 1);
        int steps  = path.size();

        if(!monster.spendMP(steps)) return;
        monster.setCell(dest);

        // Construit la string de chemin en format Dofus (simplifié — direction + cell encodée)
        StringBuilder pathStr = new StringBuilder();
        pathStr.append(encodeCellBase64(from));
        for(Short cell : path) pathStr.append(encodeCellBase64(cell));

        fight.broadcast("GA1;1;" + monster.getId() + ";" + pathStr.toString());
        logger.debug("MonsterAI : {} se déplace {} → {} ({} pas)", new Object[] { monster.getName(), from, dest, steps});
    }

    // ── Pathfinding basique ───────────────────────────────────────────────────

    /**
     * Construit un chemin en ligne droite (non diagonal) vers la cible.
     * Limité à {@code maxSteps} pas. Ne valide pas les obstacles.
     */
    private static List<Short> buildPath(short from, short to, int mapWidth, int maxSteps) {
        List<Short> path = new ArrayList<>();
        int current = from;
        int target  = to;

        for(int step = 0; step < maxSteps && current != target; step++) {
            int dx = (target % mapWidth) - (current % mapWidth);
            int dy = (target / mapWidth) - (current / mapWidth);

            // Choisit la direction principale (isométrique : ±1 col, ±mapWidth ligne)
            int delta;
            if(Math.abs(dx) >= Math.abs(dy)) {
                delta = (dx > 0) ? 1 : -1;
            } else {
                delta = (dy > 0) ? mapWidth : -mapWidth;
            }

            current += delta;
            if(current >= 0 && current < 560) {
                path.add((short) current);
            } else {
                break;
            }
        }
        return path;
    }

    /** Encode une cellule en 2 caractères base64 Dofus (table HASH). */
    private static String encodeCellBase64(short cellId) {
        final String HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        return String.valueOf(HASH.charAt(cellId / 64)) + HASH.charAt(cellId % 64);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static boolean isAdjacent(short a, short b) {
        int diff = Math.abs(a - b);
        // Voisins directs sur grille isométrique : ±1 et ±14 (largeur standard)
        return diff == 1 || diff == 14;
    }

    private static Fighter findClosestEnemy(Fighter monster, List<Fighter> enemies) {
        Fighter closest  = enemies.get(0);
        int     minDist  = Math.abs(monster.getCell() - closest.getCell());
        for(int i = 1; i < enemies.size(); i++) {
            int d = Math.abs(monster.getCell() - enemies.get(i).getCell());
            if(d < minDist) { minDist = d; closest = enemies.get(i); }
        }
        return closest;
    }

    private static List<Fighter> getEnemies(Fight fight, Fighter monster) {
        List<Fighter> enemies = new ArrayList<>();
        for(Fighter f : fight.getFighters()) {
            if(f.getTeamId() != monster.getTeamId() && !f.isDead()) enemies.add(f);
        }
        return enemies;
    }
}
