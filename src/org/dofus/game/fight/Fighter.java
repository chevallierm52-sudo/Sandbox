package org.dofus.game.fight;

import org.dofus.objects.actors.EOrientation;

/**
 * Représente un combattant dans un combat Dofus 1.29.
 *
 * Un Fighter encapsule soit un {@code Characters} (joueur ou bot),
 * soit un membre de {@code MonsterGroup}.
 *
 * Statistiques de combat (calculées au début du fight à partir du personnage/grade) :
 *   - PA / PM courants et max
 *   - PV courants et max
 *   - Stats offensives et résistances
 *
 * Positionnement :
 *   - Cellule de placement initiale (phase fP)
 *   - Cellule courante pendant le combat
 */
public class Fighter {

    public enum FighterType { PLAYER, BOT, MONSTER }

    private final int         id;           // actorId du personnage ou groupId du monstre
    private final String      name;
    private final FighterType type;
    private final int         teamId;       // 0 = attaquants, 1 = défenseurs

    // Stats de combat
    private       short currentLife;
    private final short maxLife;
    private       int   currentAP;
    private final int   baseAP;
    private       int   currentMP;
    private final int   baseMP;

    // Stats offensives (simplifiées)
    private final int strength;
    private final int agility;
    private final int intel;
    private final int chance;
    private final int wisdom;
    private       int initiative;

    // Résistances %
    private final int resNeutral;
    private final int resEarth;
    private final int resFire;
    private final int resWater;
    private final int resAir;

    // Position
    private short        cell;
    private EOrientation orientation;
    private int          level = 1;
    private int          gfxId = 0;
    private int          templateId = 0;

    // État
    private boolean dead         = false;
    private boolean turnPassed   = false;
    private boolean canPlay      = true;
    private boolean ready        = false;
    private boolean disconnected = false;

    public Fighter(int id, String name, FighterType type, int teamId,
                   short life, int ap, int mp,
                   int strength, int agility, int intel, int chance, int wisdom,
                   int resNeutral, int resEarth, int resFire, int resWater, int resAir,
                   short cell, EOrientation orientation) {
        this.id          = id;
        this.name        = name;
        this.type        = type;
        this.teamId      = teamId;
        this.currentLife = life;
        this.maxLife     = life;
        this.currentAP   = ap;
        this.baseAP      = ap;
        this.currentMP   = mp;
        this.baseMP      = mp;
        this.strength    = strength;
        this.agility     = agility;
        this.intel       = intel;
        this.chance      = chance;
        this.wisdom      = wisdom;
        this.initiative  = Math.max(1, agility + wisdom / 10);
        this.resNeutral  = resNeutral;
        this.resEarth    = resEarth;
        this.resFire     = resFire;
        this.resWater    = resWater;
        this.resAir      = resAir;
        this.cell        = cell;
        this.orientation = orientation;
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    /** Applique des dégâts en tenant compte des résistances. Retourne les dégâts réels. */
    public int takeDamage(int raw, int element) {
        int before = currentLife;
        int resist;
        switch(element) {
            case 0:  resist = resNeutral; break;
            case 1:  resist = resEarth;   break;
            case 2:  resist = resFire;    break;
            case 3:  resist = resWater;   break;
            case 4:  resist = resAir;     break;
            default: resist = 0;
        }
        // Résistance % — clamped 0-100
        int factor = Math.max(0, Math.min(100, resist));
        int damage = Math.max(1, raw - raw * factor / 100);
        int dealt = Math.min(before, damage);
        currentLife = (short) Math.max(0, before - dealt);
        if(currentLife <= 0) {
            dead = true;
            canPlay = false;
        }
        return dealt;
    }

    /** Soigne le fighter. Retourne le soin effectif appliqué. */
    public int heal(int amount) {
        int capped = Math.min(amount, maxLife - currentLife);
        currentLife = (short)(currentLife + capped);
        return capped;
    }

    /** Réinitialise PA/PM en début de tour. */
    public void resetTurn() {
        currentAP   = baseAP;
        currentMP   = baseMP;
        turnPassed  = false;
    }

    public boolean spendAP(int cost) {
        if(currentAP < cost) return false;
        currentAP -= cost;
        return true;
    }

    public boolean spendMP(int cost) {
        if(currentMP < cost) return false;
        currentMP -= cost;
        return true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int    getId()          { return id;          }
    public String getName()        { return name;        }
    public FighterType getType()   { return type;        }
    public int    getTeamId()      { return teamId;      }

    public short getCurrentLife()  { return currentLife; }
    public short getMaxLife()      { return maxLife;     }
    public int   getCurrentAP()   { return currentAP;  }
    public int   getBaseAP()      { return baseAP;     }
    public int   getCurrentMP()   { return currentMP;  }
    public int   getBaseMP()      { return baseMP;     }

    public int   getStrength()    { return strength;   }
    public int   getAgility()     { return agility;    }
    public int   getIntel()       { return intel;      }
    public int   getChance()      { return chance;     }
    public int   getWisdom()      { return wisdom;     }
    public int   getInitiative()  { return initiative; }
    public void  setInitiative(int initiative) { this.initiative = Math.max(1, initiative); }

    public int   getResNeutral()  { return resNeutral; }
    public int   getResEarth()    { return resEarth;   }
    public int   getResFire()     { return resFire;    }
    public int   getResWater()    { return resWater;   }
    public int   getResAir()      { return resAir;     }

    public short        getCell()        { return cell;        }
    public void         setCell(short c) { this.cell = c;      }
    public EOrientation getOrientation() { return orientation; }
    public void         setOrientation(EOrientation o) { this.orientation = o; }
    public int          getLevel()       { return level;       }
    public int          getGfxId()       { return gfxId;       }
    public int          getTemplateId()  { return templateId;  }
    public void         setVisual(int level, int gfxId) {
        this.level = Math.max(1, level);
        this.gfxId = Math.max(0, gfxId);
    }
    public void         setTemplateId(int templateId) { this.templateId = templateId; }

    public boolean isDead()        { return dead;        }
    public boolean hasTurnPassed() { return turnPassed;  }
    public void    setTurnPassed(boolean b) { turnPassed = b; }
    public boolean canPlay()       { return canPlay && !dead && !disconnected; }
    public void    setCanPlay(boolean b) { canPlay = b; }
    public boolean isReady()       { return ready; }
    public void    setReady(boolean ready) { this.ready = ready; }
    public boolean isDisconnected(){ return disconnected; }
    public void    setDisconnected(boolean disconnected) { this.disconnected = disconnected; }

    /** Paquet de présence dans la liste combat (affiché dans l'interface fP). */
    public String toFLEntry() {
        // Format : id|name|life|maxLife|ap|mp|resist...
        // TODO : format exact Dofus 1.29
        return id + "|" + name + "|" + currentLife + "|" + maxLife + "|"
             + currentAP + "|" + currentMP + "|" + teamId;
    }
}
