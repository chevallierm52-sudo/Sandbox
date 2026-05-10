package org.dofus.game.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.session.IoSession;
import org.dofus.database.objects.CharactersData;
import org.dofus.objects.WorldData;
import org.dofus.objects.actors.Characters;
import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.monsters.MonsterGroup;
import org.dofus.objects.monsters.MonsterTemplate;
import org.dofus.objects.spells.SpellTemplate;
import org.dofus.database.objects.SpellsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moteur de combat Dofus 1.29.
 *
 * Cycle de vie : PLACEMENT → ACTIVE → FINISHED
 *
 * Protocole (résumé) :
 *   Démarrage   — fJK, fPS, fPK
 *   Combat      — fTH (début tour), GA (action), fTN (fin tour)
 *   Fin         — fR (résultat) + GM|+ respawn
 *
 * Ordre de tour : calculé selon l'initiative (agilité + random).
 * IA monstres  : {@link MonsterAI#playTurn(Fight, Fighter)}.
 * Drops        : {@link DropTable#roll(int, int)}.
 */
public class Fight {

    private static final Logger logger = LoggerFactory.getLogger(Fight.class);
    private static final AtomicInteger FIGHT_ID_GEN = new AtomicInteger(1);

    // ── État ──────────────────────────────────────────────────────────────────

    public enum State { PLACEMENT, ACTIVE, FINISHED }

    private final int         id;
    private final MapTemplate map;
    private       State       state = State.PLACEMENT;

    /** Fighters ordonnés à l'insertion, puis triés par initiative au démarrage. */
    private final LinkedHashMap<Integer, Fighter> fighters = new LinkedHashMap<>();

    /** Groupe de monstres impliqué (null si PvP). */
    private MonsterGroup monsterGroup;

    /** Registre global des combats actifs : fightId → Fight */
    private static final ConcurrentHashMap<Integer, Fight> activeFights = new ConcurrentHashMap<>();

    private final FightTurn  turn;
    private List<Fighter>    turnOrder;
    private int              turnIndex = 0;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public Fight(MapTemplate map) {
        this.id   = FIGHT_ID_GEN.getAndIncrement();
        this.map  = map;
        this.turn = new FightTurn(this);
        activeFights.put(this.id, this);
        logger.info("Fight {} créé sur map {}", id, map.getId());
    }

    // ── Gestion des combattants ───────────────────────────────────────────────

    public void addFighter(Fighter fighter) {
        fighters.put(fighter.getId(), fighter);
    }

    public void setMonsterGroup(MonsterGroup group) {
        this.monsterGroup = group;
    }

    public Fighter getFighter(int id) {
        return fighters.get(id);
    }

    /**
     * Retire un fighter du combat (quitter en placement).
     * Si plus assez de combattants, annule le combat.
     */
    public void removeFighter(int fighterId) {
        fighters.remove(fighterId);
        if(turnOrder != null) {
            turnOrder.removeIf(f -> f.getId() == fighterId);
        }
        // Si une équipe est vide après retrait → annulation
        if(state == State.PLACEMENT && (getTeam(0).isEmpty() || getTeam(1).isEmpty())) {
            cancelFight();
        }
    }

    /** Annule le combat (placement abandonné). */
    public void cancelFight() {
        state = State.FINISHED;
        activeFights.remove(id);
        broadcast("fA"); // fight annulé
        logger.info("Fight {} annulé (abandon placement)", id);
    }

    public List<Fighter> getFighters() {
        return new ArrayList<>(fighters.values());
    }

    public List<Fighter> getTeam(int teamId) {
        List<Fighter> team = new ArrayList<>();
        for(Fighter f : fighters.values()) if(f.getTeamId() == teamId) team.add(f);
        return team;
    }

    // ── Démarrage ─────────────────────────────────────────────────────────────

    /**
     * Termine la phase de placement et lance le combat actif.
     * Ordre de tour = agilité décroissante + aléatoire à égalité.
     */
    public void startFight() {
        state     = State.ACTIVE;
        turnOrder = new ArrayList<>(fighters.values());

        // Tri par initiative : agilité + random(0, wisdom/10)
        Collections.sort(turnOrder, new Comparator<Fighter>() {
            public int compare(Fighter a, Fighter b) {
                int ia = a.getAgility() + (int)(Math.random() * Math.max(1, a.getWisdom() / 10 + 1));
                int ib = b.getAgility() + (int)(Math.random() * Math.max(1, b.getWisdom() / 10 + 1));
                return Integer.compare(ib, ia); // décroissant
            }
        });
        turnIndex = 0;

        broadcast("fPK"); // fin placement
        broadcast("fD1"); // début combat

        nextTurn();
    }

    // ── Boucle de tours ───────────────────────────────────────────────────────

    public void nextTurn() {
        if(state == State.FINISHED) return;

        if(checkWinCondition()) {
            endFight();
            return;
        }

        // Cherche le prochain fighter vivant
        int checked = 0;
        Fighter next = null;
        while(checked < turnOrder.size()) {
            if(turnIndex >= turnOrder.size()) turnIndex = 0;
            Fighter candidate = turnOrder.get(turnIndex++);
            if(candidate.canPlay()) { next = candidate; break; }
            checked++;
        }

        if(next == null) { endFight(); return; }

        // IA monstre : passe la main directement à MonsterAI
        if(next.getType() == Fighter.FighterType.MONSTER) {
            turn.startTurn(next);
            MonsterAI.playTurn(this, next);
        } else {
            turn.startTurn(next);
        }
    }

    // ── Actions de combat ─────────────────────────────────────────────────────

    /**
     * Traitement d'une action GA du client.
     *
     * @param fighter  Fighter qui agit
     * @param actionId 1=mouvement, 300+=sort
     * @param args     Arguments
     */
    public void handleAction(Fighter fighter, int actionId, String args) {
        if(state != State.ACTIVE) return;
        if(turn.getCurrentFighter() == null || turn.getCurrentFighter().getId() != fighter.getId()) return;

        switch(actionId) {
            case 1:
                handleMove(fighter, args);
                break;
            default:
                if(actionId >= 300 && actionId < 600) {
                    handleSpell(fighter, actionId - 300, args);
                } else {
                    logger.debug("Fight {} : action {} non gérée (args={})", new Object[] { id, actionId, args});
                }
        }
    }

    private void handleMove(Fighter fighter, String pathStr) {
        // Sécurité longueur minimale du chemin
        if(pathStr == null || pathStr.length() < 2) return;

        // Consomme 1 PM par pas (1 pas = 2 chars encodés - 1 départ)
        int steps = Math.max(0, (pathStr.length() / 2) - 1);
        if(steps > 0 && !fighter.spendMP(steps)) {
            logger.debug("Fight {} : {} pas insuffisants pour {}", new Object[] { id, steps, fighter.getName()});
            return;
        }

        // Mise à jour de la cellule (dernier couple de chars du path)
        if(pathStr.length() >= 2) {
            String lastPair = pathStr.substring(pathStr.length() - 2);
            try {
                short newCell = decodeCellBase64(lastPair);
                fighter.setCell(newCell);
            } catch(Exception e) {
                // chemin malformé — on diffuse quand même pour la cohérence visuelle
            }
        }

        broadcast("GA1;1;" + fighter.getId() + ";" + pathStr);
    }

    private void handleSpell(Fighter fighter, int spellId, String args) {
        // args = "targetCell" ou "targetFighterId;targetCell"
        SpellTemplate spell = SpellsData.getTemplate(spellId);
        if(spell == null) {
            logger.debug("Fight {} : sort {} introuvable", id, spellId);
            return;
        }

        int spellLevel = 1; // TODO : niveau de sort réel du personnage
        SpellTemplate.SpellLevel level = spell.getLevel(spellLevel);
        if(level == null || !fighter.spendAP(level.getApCost())) return;

        // Parse la cellule cible
        short targetCell;
        try {
            String cellPart = args.contains(";") ? args.split(";")[1] : args;
            targetCell = decodeCellBase64(cellPart);
        } catch(Exception e) {
            return;
        }

        // Applique les effets sur les fighters dans la zone (simplifiée : cellule unique)
        Fighter target = findFighterOnCell(targetCell);
        if(target != null) {
            for(SpellTemplate.SpellEffect effect : level.getEffects()) {
                applyEffect(fighter, target, effect);
            }
        }

        // Diffuse l'animation du sort
        broadcast("GA" + (300 + spellId) + ";" + fighter.getId() + ";" +
                  (target != null ? target.getId() : "") + ";" + encodeCellBase64(targetCell));
    }

    private void applyEffect(Fighter caster, Fighter target, SpellTemplate.SpellEffect effect) {
        int effectId = effect.getEffectId();
        int value    = effect.roll();

        // Bonus offensif : force/intelligence/chance/agilité selon l'élément
        int statBonus = getStatForElement(caster, effect.getElement());
        int raw       = value + statBonus / 10;

        if(effectId == 108) {
            // Soin
            int healed = target.heal(raw);
            broadcast("GA108;" + caster.getId() + ";" + target.getId() + ";" + healed);
            broadcast("GA306;" + caster.getId() + ";" + target.getId() + ";" + target.getCurrentLife());
        } else if(effectId >= 91 && effectId <= 96) {
            // Dégâts (91=neutre 92=air 93=feu 94=eau 95=terre 96=neutre boost)
            int element = effectIdToElement(effectId);
            int dealt   = target.takeDamage(raw, element);
            broadcast("GA" + effectId + ";" + caster.getId() + ";" + target.getId() + ";" + dealt);
            broadcast("GA306;" + caster.getId() + ";" + target.getId() + ";" + target.getCurrentLife());
            if(target.isDead()) {
                broadcast("GA402;" + target.getId() + ";0");
                logger.debug("Fight {} : {} tué par {}", new Object[] { id, target.getName(), caster.getName()});
            }
        }
    }

    // ── Fin de combat ─────────────────────────────────────────────────────────

    private boolean checkWinCondition() {
        boolean t0alive = false, t1alive = false;
        for(Fighter f : fighters.values()) {
            if(!f.isDead()) {
                if(f.getTeamId() == 0) t0alive = true;
                else                   t1alive = true;
            }
        }
        return !t0alive || !t1alive;
    }

    /**
     * Termine le combat : calcule l'XP, les drops, distribue les récompenses,
     * remet les joueurs survivants sur la carte.
     */
    private void endFight() {
        if(state == State.FINISHED) return;
        state = State.FINISHED;
        activeFights.remove(id);
        if(turn.getTurnTimer() != null) turn.getTurnTimer().cancel(false);

        // Équipe victorieuse = celle qui a encore des survivants
        int winnerTeam = -1;
        for(Fighter f : fighters.values()) {
            if(!f.isDead()) { winnerTeam = f.getTeamId(); break; }
        }

        // ── XP et drops (victoire des joueurs = team 0) ───────────────────────
        int totalXp     = 0;
        int totalKamas  = 0;
        List<DropTable.DropResult> drops = new ArrayList<>();
        int prospection = calculateTeamProspection(0);

        if(winnerTeam == 0 && monsterGroup != null) {
            // XP du groupe de monstres
            for(MonsterGroup.MonsterEntry entry : monsterGroup.getMembers()) {
                MonsterTemplate.MonsterGrade grade = entry.getTemplate().getGrade(entry.getGrade());
                if(grade != null) {
                    totalXp    += grade.getXpBase();
                    totalKamas += DropTable.rollKamas(grade, prospection);
                    drops.addAll(DropTable.roll(entry.getTemplate().getId(), prospection));
                }
            }
        }

        // ── Paquet fR (résultat) ──────────────────────────────────────────────
        // Format simplifié : fR{winnerTeam};{survivors info}
        StringBuilder fR = new StringBuilder("fR");
        fR.append(winnerTeam).append(";");
        for(Fighter f : fighters.values()) {
            fR.append(f.getId()).append("|")
              .append(f.isDead() ? 0 : 1).append(";");
        }
        broadcast(fR.toString());

        // ── Distribution XP / kamas aux joueurs vainqueurs ───────────────────
        List<Fighter> winners = getTeam(winnerTeam);
        if(!winners.isEmpty() && (totalXp > 0 || totalKamas > 0)) {
            int xpPerFighter = Math.max(1, totalXp / winners.size());
            for(Fighter f : winners) {
                Characters chr = WorldData.getCharacterById(f.getId());
                if(chr == null) continue;
                IoSession sess = WorldData.getSessionByAccount().get(chr.getOwner());

                // XP
                if(totalXp > 0) {
                    chr.getExperience().add(xpPerFighter);
                }
                // Kamas
                if(totalKamas > 0) {
                    chr.setKamas(chr.getKamas() + totalKamas);
                    if(sess != null && sess.isConnected())
                        sess.write("Of+" + totalKamas);
                }
                // Drops (tous au premier survivant — TODO : sac de combat)
                if(!drops.isEmpty() && f == winners.get(0) && sess != null && sess.isConnected()) {
                    for(DropTable.DropResult drop : drops) {
                        sess.write("OA" + drop.templateId + "|" + drop.quantity + "|0");
                    }
                }
                // Réinitialise la regen
                if(sess != null && sess.isConnected()) {
                    org.dofus.utils.RegenService.start(chr);
                }
                // Sauvegarde
                CharactersData.update(chr);
            }
        }

        // ── Respawn des survivants sur la carte ───────────────────────────────
        for(Fighter f : fighters.values()) {
            if(!f.isDead()) {
                Characters chr = WorldData.getCharacterById(f.getId());
                if(chr != null) {
                    chr.setCurrentCell(f.getCell());
                    IoSession sess = WorldData.getSessionByAccount().get(chr.getOwner());
                    if(sess != null && sess.isConnected()) {
                        // Renvoi le joueur sur la carte
                        sess.write("GI");
                    }
                }
            }
        }

        // Retire le groupe de monstres de la carte si les joueurs ont gagné
        if(winnerTeam == 0 && monsterGroup != null) {
            map.removeMonsterGroup(monsterGroup);
            // Respawn planifié via MapRespawnService
            org.dofus.utils.MapRespawnService.scheduleRespawn(map, monsterGroup);
        }

        logger.info("Fight {} terminé — gagnant: team{} xp={} kamas={} drops={}",
        		new Object[] { id, winnerTeam, totalXp, totalKamas, drops.size()});
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    public void broadcast(String packet) {
        for(Fighter f : new ArrayList<>(fighters.values())) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr == null) continue;
            IoSession session = WorldData.getSessionByAccount().get(chr.getOwner());
            if(session != null && session.isConnected()) session.write(packet);
        }
    }

    // ── Utilitaires privés ────────────────────────────────────────────────────

    private int calculateTeamProspection(int teamId) {
        int total = 100; // base
        for(Fighter f : getTeam(teamId)) {
            Characters chr = WorldData.getCharacterById(f.getId());
            if(chr != null) {
                // Prospection = chance/10 + stat prospection (TODO stats items)
                total += chr.getStats().getEffect(176) + // ADD_PROSPECTING
                         f.getChance() / 10;
            }
        }
        return total;
    }

    private Fighter findFighterOnCell(short cell) {
        for(Fighter f : fighters.values()) {
            if(!f.isDead() && f.getCell() == cell) return f;
        }
        return null;
    }

    private int getStatForElement(Fighter f, int element) {
        switch(element) {
            case 1: return f.getStrength();
            case 2: return f.getIntel();
            case 3: return f.getChance();
            case 4: return f.getAgility();
            default: return f.getStrength();
        }
    }

    private int effectIdToElement(int effectId) {
        switch(effectId) {
            case 91: return 0; // neutre
            case 92: return 4; // air
            case 93: return 2; // feu
            case 94: return 3; // eau
            case 95: return 1; // terre
            default: return 0;
        }
    }

    /** Décode 2 caractères base64 Dofus en cellId. */
    private static short decodeCellBase64(String s) {
        final String HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        if(s == null || s.length() < 2) throw new IllegalArgumentException("cell trop courte");
        return (short)(HASH.indexOf(s.charAt(0)) * 64 + HASH.indexOf(s.charAt(1)));
    }

    /** Encode un cellId en 2 caractères base64 Dofus. */
    static String encodeCellBase64(short cellId) {
        final String HASH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        return String.valueOf(HASH.charAt(cellId / 64)) + HASH.charAt(cellId % 64);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int         getId()       { return id;    }
    public MapTemplate getMap()      { return map;   }
    public State       getState()    { return state; }
    public FightTurn   getTurn()     { return turn;  }

    public static Fight getFight(int fightId) { return activeFights.get(fightId); }
    public static Map<Integer, Fight> getActiveFights() {
        return java.util.Collections.unmodifiableMap(activeFights);
    }
}
