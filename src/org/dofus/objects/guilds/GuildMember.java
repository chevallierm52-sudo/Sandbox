package org.dofus.objects.guilds;

/**
 * Membre d'une guilde Dofus 1.29.
 *
 * Rang : valeur entière (1-10 environ).
 *   1 = Aspirant
 *   5 = Mémbr
 *   9 = Second
 *   10 = Meneur (une seule personne)
 *
 * Droits : bitmask (voir {@link Guild} pour la description des bits).
 */
public class GuildMember {

    private final int    characterId;
    private final String characterName;
    private       int    rank;
    private       int    rights;
    private       long   experience;   // XP donnée à la guilde par ce membre
    private       int    level;        // niveau du personnage (cache, mis à jour au login)
    private       int    breed;        // race du personnage
    private       byte   gender;
    private       boolean online;

    public GuildMember(int characterId, String characterName, int rank, int rights,
                       long experience, int level, int breed, byte gender) {
        this.characterId   = characterId;
        this.characterName = characterName;
        this.rank          = rank;
        this.rights        = rights;
        this.experience    = experience;
        this.level         = level;
        this.breed         = breed;
        this.gender        = gender;
        this.online        = false;
    }

    // ── Droits ────────────────────────────────────────────────────────────────

    public boolean canManageMembers()    { return (rights & 1)  != 0; }
    public boolean canCollect()          { return (rights & 2)  != 0; }
    public boolean canPlaceTaxCollector(){ return (rights & 4)  != 0; }
    public boolean canDefend()           { return (rights & 8)  != 0; }

    // ── Sérialisation ─────────────────────────────────────────────────────────

    /**
     * Entrée dans le paquet {@code gL}.
     * Format TODO : characterId|name|level|breed|gender|rank|rights|xp|online
     */
    public String toGLEntry() {
        return characterId + "|" + characterName + "|" + level + "|"
             + breed + "|" + gender + "|" + rank + "|" + rights + "|"
             + experience + "|" + (online ? 1 : 0);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int     getCharacterId()   { return characterId;   }
    public String  getCharacterName() { return characterName; }
    public int     getRank()          { return rank;          }
    public void    setRank(int r)     { this.rank = r;        }
    public int     getRights()        { return rights;        }
    public void    setRights(int r)   { this.rights = r;      }
    public long    getExperience()    { return experience;    }
    public void    addExperience(long xp) { this.experience += xp; }
    public int     getLevel()         { return level;         }
    public void    setLevel(int l)    { this.level = l;       }
    public int     getBreed()         { return breed;         }
    public byte    getGender()        { return gender;        }
    public boolean isOnline()         { return online;        }
    public void    setOnline(boolean b) { this.online = b;    }
}
