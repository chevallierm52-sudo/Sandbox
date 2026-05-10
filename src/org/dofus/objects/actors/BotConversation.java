package org.dofus.objects.actors;

import java.util.HashMap;
import java.util.Map;

/**
 * Moteur de conversation contextuelle pour les bots.
 *
 * Chaque personnalité dispose de pools de messages adaptés à la situation :
 *   - chat général (wandering)
 *   - annonce d'exploration
 *   - annonce d'arrivée sur une nouvelle map
 *   - réaction à un message de map (d'un joueur ou d'un autre bot)
 *   - réponse à un message privé
 */
public class BotConversation {

    // ── Messages généraux par personnalité ────────────────────────────────────

    private static final Map<BotPersonality, String[]> GENERAL = new HashMap<>();
    static {
        GENERAL.put(BotPersonality.EXPLORER, new String[]{
            "Je me demande ce qu'il y a sur la prochaine map...",
            "Il parait qu'il y a des ressources rares par ici.",
            "J'ai déjà exploré 80 maps ce mois-ci !",
            "Quelqu'un sait si les Blops sont au nord ou au sud ?",
            "J'adore découvrir de nouvelles zones.",
            "Cette map est superbe sous cet angle.",
            "J'ai trouvé un raccourci hier, c'est incroyable.",
            "Toujours en mouvement !",
        });
        GENERAL.put(BotPersonality.SOCIAL, new String[]{
            "Bonjour tout le monde !",
            "Comment ça va aujourd'hui ?",
            "Quelqu'un cherche un groupe pour un donjon ?",
            "Je cherche un craft, quelqu'un peut m'aider ?",
            "On fait un donjeon ce soir ?",
            "Super ambiance ici comme toujours !",
            "N'hésitez pas si vous avez besoin d'aide.",
            "Bonne chasse à tous !",
            "Quelqu'un peut m'expliquer le système d'alignement ?",
        });
        GENERAL.put(BotPersonality.MERCHANT, new String[]{
            "Achat blé, froment et houblon — bon prix !",
            "Vente parchemins de vitalité x10.",
            "Le marché est animé en ce moment.",
            "J'ai des ressources craft disponibles, MP.",
            "WTB minerai de cuivre, contactez-moi.",
            "Échange possible, faites-moi une offre.",
            "Les prix ont changé depuis la dernière MAJ.",
            "Vente set Bouftou complet, prix à négocier.",
        });
        GENERAL.put(BotPersonality.WARRIOR, new String[]{
            "Qui veut farmer des Chafers ce soir ?",
            "Ces mobs drop enfin quelque chose d'utile !",
            "Mon Iop lvl 95 cherche un groupe PvP.",
            "Le boss de ce donjon est vraiment chaud.",
            "J'ai enfin le set complet pour ce combat !",
            "3 morts de suite... je rage.",
            "GG à ceux qui font des 200 solo.",
            "Quelqu'un pour un duel amical ?",
        });
    }

    // ── Annonces d'exploration ────────────────────────────────────────────────

    private static final Map<BotPersonality, String[]> EXPLORE_START = new HashMap<>();
    static {
        EXPLORE_START.put(BotPersonality.EXPLORER, new String[]{
            "Je vais explorer vers le nord, à tout !",
            "Allez, je pars découvrir la suite !",
            "Curiosité oblige, je continue ma route.",
            "Je vais voir ce qu'il y a de l'autre côté.",
            "En route pour la prochaine zone !",
        });
        EXPLORE_START.put(BotPersonality.SOCIAL, new String[]{
            "Je vais jeter un oeil à côté, vous venez ?",
            "Hop, je change de map ! Quelqu'un suit ?",
            "Je vais voir mes amis sur la prochaine map.",
        });
        EXPLORE_START.put(BotPersonality.MERCHANT, new String[]{
            "Je vais chercher des ressources ailleurs.",
            "Les prix sont meilleurs par là-bas.",
        });
        EXPLORE_START.put(BotPersonality.WARRIOR, new String[]{
            "Je vais chercher des mobs plus forts.",
            "Le spawn est meilleur sur la prochaine zone.",
            "En route pour un meilleur spot de farm !",
        });
    }

    // ── Annonces d'arrivée sur une nouvelle map ───────────────────────────────

    private static final Map<BotPersonality, String[]> ARRIVE = new HashMap<>();
    static {
        ARRIVE.put(BotPersonality.EXPLORER, new String[]{
            "Nouvelle map, nouvelle aventure !",
            "Ah, je connais pas encore cette zone.",
            "Sympa par ici, je note l'endroit.",
            "Cool, une zone que je n'avais pas encore explorée.",
        });
        ARRIVE.put(BotPersonality.SOCIAL, new String[]{
            "Salut tout le monde !",
            "Bonjour la nouvelle map !",
            "Content d'être arrivé ici !",
        });
        ARRIVE.put(BotPersonality.MERCHANT, new String[]{
            "Voyons voir ce qu'on trouve ici.",
            "Nouvelle zone, nouvelles opportunités.",
        });
        ARRIVE.put(BotPersonality.WARRIOR, new String[]{
            "Bon, les mobs ont intérêt à être à la hauteur.",
            "Nouveau terrain de jeu !",
        });
    }

    // ── Réactions à un message de map ────────────────────────────────────────

    private static final Map<BotPersonality, String[]> REACT = new HashMap<>();
    static {
        REACT.put(BotPersonality.EXPLORER, new String[]{
            "Ah ouais ?", "Intéressant !", "Je savais pas ça.",
            "Tu connais la zone ?", "J'ai vu pareil de l'autre côté.",
        });
        REACT.put(BotPersonality.SOCIAL, new String[]{
            "Haha !", "Exactement !", "Je suis d'accord.",
            "Tu as raison !", "C'est vrai ça !", "Moi aussi !",
            "Sympa comme endroit hein ?",
        });
        REACT.put(BotPersonality.MERCHANT, new String[]{
            "Le marché est bon en ce moment.",
            "J'ai vu des ressources dans ce coin.",
            "Tu vends ou tu achètes ?",
        });
        REACT.put(BotPersonality.WARRIOR, new String[]{
            "Ouais le farm est sympa ici.", "T'as vu le spawn ?",
            "Les mobs drop bien.", "T'es quel lvl toi ?",
        });
    }

    // ── Réponses à un MP ──────────────────────────────────────────────────────

    private static final Map<BotPersonality, String[]> PM_REPLY = new HashMap<>();
    static {
        PM_REPLY.put(BotPersonality.EXPLORER, new String[]{
            "Salut ! Je suis en route pour une nouvelle zone.",
            "Hey ! Je peux pas trop parler je suis en exploration.",
            "Bonjour ! Tu connais des zones sympas par ici ?",
            "Coucou ! J'explore actuellement, on se parle après ?",
        });
        PM_REPLY.put(BotPersonality.SOCIAL, new String[]{
            "Salut ! Comment ça va ?",
            "Hey ! Content de te parler !",
            "Coucou ! Tu cherches quelque chose ?",
            "Bonjour ! Je suis là si t'as besoin.",
            "Sympa d'avoir des nouvelles !",
        });
        PM_REPLY.put(BotPersonality.MERCHANT, new String[]{
            "Bonjour. Tu veux acheter ou vendre quelque chose ?",
            "Salut. J'ai des ressources si t'en as besoin.",
            "Hey. Tu cherches un craft ou des kamas ?",
        });
        PM_REPLY.put(BotPersonality.WARRIOR, new String[]{
            "Yo ! Tu cherches un groupe de farm ?",
            "Salut. Tu veux faire un combat ?",
            "Hey ! T'as vu les drops récemment ?",
        });
    }

    // ── Annonce de suivi d'un ami ─────────────────────────────────────────────

    private static final String[] FOLLOW_ANNOUNCE = {
        "J'arrive %s !",
        "J'arrive, j'arrive !",
        "Attends-moi %s, j'arrive !",
        "Je te suis %s.",
        "Je te rejoins !",
    };

    // ── API publique ──────────────────────────────────────────────────────────

    public static String getGeneralMessage(BotPersonality p) {
        return pick(GENERAL.get(p));
    }

    public static String getExploreAnnounce(BotPersonality p) {
        return pick(EXPLORE_START.get(p));
    }

    public static String getArriveMessage(BotPersonality p) {
        return pick(ARRIVE.get(p));
    }

    public static String getReaction(BotPersonality p) {
        return pick(REACT.get(p));
    }

    public static String getPMReply(BotPersonality p) {
        return pick(PM_REPLY.get(p));
    }

    public static String getFollowAnnounce(String friendName) {
        String tpl = FOLLOW_ANNOUNCE[(int)(Math.random() * FOLLOW_ANNOUNCE.length)];
        return tpl.contains("%s") ? String.format(tpl, friendName) : tpl;
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static String pick(String[] pool) {
        if(pool == null || pool.length == 0) return "...";
        return pool[(int)(Math.random() * pool.length)];
    }
}
