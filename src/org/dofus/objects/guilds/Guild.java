package org.dofus.objects.guilds;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Guilde Dofus 1.29.
 *
 * Fonctionnalités :
 *   - Création : paquet {@code gC}
 *   - Dissolution : paquet {@code gD}
 *   - Invitation : {@code gI} + acceptation {@code gJ}
 *   - Kick : {@code gK}
 *   - Liste membres : {@code gL}
 *   - Message guilde : canal {@code $} (paquet {@code cMK$...})
 *   - Percepteur : {@code gP} (spawn sur carte)
 *
 * Droits des membres : bitmask Dofus 1.29
 *   bit 1  = gérer les membres
 *   bit 2  = droits de collecte percepteur
 *   bit 4  = poser des percepteurs
 *   bit 8  = lancer des défenses
 *   TODO : implémenter les droits complets
 */
public class Guild {

    private final int    id;
    private       String name;
    private       String emblem;  // code graphique de l'emblème (TODO : format exact)
    private       int    level;
    private       long   experience;

    /** Membres de la guilde : characterId → GuildMember */
    private final ConcurrentMap<Integer, GuildMember> members = new ConcurrentHashMap<>();

    public Guild(int id, String name, String emblem, int level, long experience) {
        this.id         = id;
        this.name       = name;
        this.emblem     = emblem;
        this.level      = level;
        this.experience = experience;
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    public void addMember(GuildMember member) {
        members.put(member.getCharacterId(), member);
    }

    public void removeMember(int characterId) {
        members.remove(characterId);
    }

    public GuildMember getMember(int characterId) {
        return members.get(characterId);
    }

    public Collection<GuildMember> getMembers() {
        return Collections.unmodifiableCollection(members.values());
    }

    public boolean hasMember(int characterId) {
        return members.containsKey(characterId);
    }

    public int getMemberCount() { return members.size(); }

    /** Retourne le membre avec le rang le plus élevé (meneur). */
    public GuildMember getLeader() {
        GuildMember leader = null;
        for(GuildMember m : members.values()) {
            if(leader == null || m.getRank() > leader.getRank()) leader = m;
        }
        return leader;
    }

    // ── Sérialisation protocole ───────────────────────────────────────────────

    /**
     * Paquet {@code gI} — info guilde (affiché dans la fenêtre guilde).
     * TODO : format exact Dofus 1.29
     */
    public String toGIPacket() {
        return "gI" + id + "|" + name + "|" + emblem + "|" + level + "|" + experience + "|" + members.size();
    }

    /**
     * Paquet {@code gL} — liste des membres.
     * TODO : format exact Dofus 1.29
     */
    public String toGLPacket() {
        StringBuilder sb = new StringBuilder("gL");
        for(GuildMember m : members.values()) {
            sb.append(m.toGLEntry()).append('|');
        }
        return sb.toString();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getId()         { return id;         }
    public String getName()       { return name;       }
    public void   setName(String n) { this.name = n;   }
    public String getEmblem()     { return emblem;     }
    public void   setEmblem(String e) { this.emblem = e; }
    public int    getLevel()      { return level;      }
    public void   setLevel(int l) { this.level = l;    }
    public long   getExperience() { return experience; }
    public void   addExperience(long xp) { this.experience += xp; }
}
