package org.dofus.objects.actors;

/**
 * Personnalité d'un bot — détermine ses poids comportementaux.
 *
 * exploreWeight  : probabilité de chercher activement un changement de map
 * talkWeight     : fréquence de chat spontané (multiplie l'intervalle de base)
 * replyWeight    : probabilité de répondre à un message de map ou MP
 * groupWeight    : probabilité d'accepter/proposer un groupe
 * followWeight   : probabilité de suivre un ami vers une nouvelle map
 */
public enum BotPersonality {

    //                      explore  talk   reply  group  follow
    EXPLORER(               0.70,    0.35,  0.40,  0.40,  0.65),
    SOCIAL  (               0.15,    0.85,  0.80,  0.75,  0.50),
    MERCHANT(               0.08,    0.60,  0.70,  0.30,  0.10),
    WARRIOR (               0.30,    0.55,  0.45,  0.55,  0.40);

    private final double exploreWeight;
    private final double talkWeight;
    private final double replyWeight;
    private final double groupWeight;
    private final double followWeight;

    BotPersonality(double e, double t, double r, double g, double f) {
        this.exploreWeight = e;
        this.talkWeight    = t;
        this.replyWeight   = r;
        this.groupWeight   = g;
        this.followWeight  = f;
    }

    public double getExploreWeight() { return exploreWeight; }
    public double getTalkWeight()    { return talkWeight;    }
    public double getReplyWeight()   { return replyWeight;   }
    public double getGroupWeight()   { return groupWeight;   }
    public double getFollowWeight()  { return followWeight;  }
}
