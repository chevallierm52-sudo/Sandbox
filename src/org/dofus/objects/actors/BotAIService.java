package org.dofus.objects.actors;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service IA pour les bots — deux modes :
 *
 *  1. TEMPLATE (défaut, sans API key) : réponds depuis {@link BotConversation}.
 *  2. OPENAI (si bot.ai.key est configuré et bot.ai.enabled=true) :
 *     appel asynchrone à l'API OpenAI (gpt-3.5-turbo par défaut).
 *     Rate-limit : 1 appel par bot par minute maximum.
 *     En cas d'erreur ou timeout, fallback sur le mode template.
 *
 * Config dans config.properties :
 *   bot.ai.enabled=false
 *   bot.ai.key=sk-...
 *   bot.ai.model=gpt-3.5-turbo
 */
public class BotAIService {

    private static final Logger logger = LoggerFactory.getLogger(BotAIService.class);

    private static boolean  enabled   = false;
    private static String   apiKey    = "";
    private static String   model     = "gpt-3.5-turbo";
    private static final long RATE_LIMIT_MS = 60_000L; // 1 min entre appels par bot

    /** botId → timestamp du dernier appel OpenAI */
    private static final Map<Integer, Long> lastCall = new ConcurrentHashMap<>();

    /** Pattern pour extraire "content" de la réponse JSON OpenAI. */
    private static final Pattern CONTENT_PATTERN =
        Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ── Configuration ─────────────────────────────────────────────────────────

    public static void configure(Properties config) {
        apiKey  = config.getProperty("bot.ai.key", "").trim();
        model   = config.getProperty("bot.ai.model", "gpt-3.5-turbo").trim();
        enabled = !apiKey.isEmpty()
               && Boolean.parseBoolean(config.getProperty("bot.ai.enabled", "false"));

        if(enabled) {
            logger.info("BotAIService : OpenAI activé (model={})", model);
        } else {
            logger.info("BotAIService : mode template (OpenAI désactivé)");
        }
    }

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Génère une réponse pour le bot de façon asynchrone.
     * Le callback reçoit la réponse dès qu'elle est prête.
     *
     * @param bot         Le bot qui parle
     * @param personality La personnalité du bot
     * @param context     Le contexte (message reçu, situation)
     * @param callback    Appelé avec la réponse finale (jamais null)
     */
    public static void getResponse(Characters bot, BotPersonality personality,
                                   String context, Consumer<String> callback) {
        if(!enabled || isRateLimited(bot.getId())) {
            callback.accept(BotConversation.getPMReply(personality));
            return;
        }

        lastCall.put(bot.getId(), System.currentTimeMillis());

        // Appel asynchrone sur le scheduler des bots
        BotBehavior.schedule(() -> {
            String response = null;
            try {
                response = callOpenAI(bot.getName(), personality, context);
            } catch(Exception e) {
                logger.warn("OpenAI call failed for bot {}: {}", bot.getName(), e.getMessage());
            }
            // Fallback si l'appel échoue
            if(response == null || response.trim().isEmpty()) {
                response = BotConversation.getPMReply(personality);
            } else {
                // Soumet la réponse OpenAI dans le moteur d'apprentissage.
                // Elle sera renforcée si un joueur répond dans la fenêtre de réaction.
                BotLearning.submitPhrase(personality, response);
            }
            callback.accept(response);
        }, 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * Demande un avis court a ChatGPT pour une decision combat.
     * Retourne null si OpenAI est desactive ou rate-limit, afin de garder
     * la decision heuristique locale comme source principale.
     */
    public static void getCombatAdvice(Characters bot, BotPersonality personality,
                                       String context, Consumer<String> callback) {
        if(!enabled || isRateLimited(bot.getId())) {
            callback.accept(null);
            return;
        }

        lastCall.put(bot.getId(), System.currentTimeMillis());
        BotBehavior.schedule(() -> {
            String response = null;
            try {
                response = callOpenAI(buildCombatBody(bot.getName(), personality, context));
            } catch(Exception e) {
                logger.warn("OpenAI combat advice failed for bot {}: {}", bot.getName(), e.getMessage());
            }
            callback.accept(response);
        }, 0L, TimeUnit.MILLISECONDS);
    }

    // ── Appel HTTP OpenAI ─────────────────────────────────────────────────────

    private static String callOpenAI(String botName, BotPersonality personality,
                                     String context) throws Exception {
        return callOpenAI(buildBody(botName, personality, context));
    }

    private static String callOpenAI(String body) throws Exception {
        URL url = new URL("https://api.openai.com/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);

        try(OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();
        if(status != 200) {
            logger.warn("OpenAI HTTP {}", status);
            return null;
        }

        StringBuilder sb = new StringBuilder();
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while((line = br.readLine()) != null) sb.append(line);
        }

        return parseContent(sb.toString());
    }

    private static String buildBody(String botName, BotPersonality personality, String context) {
        String systemPrompt = "Tu es " + botName + ", un joueur du MMORPG Dofus 1.29. "
            + "Ta personnalité : " + personalityDesc(personality) + ". "
            + "Réponds en français, de façon naturelle et courte (1-2 phrases max). "
            + "Tu es un vrai joueur, pas un assistant. Parle de Dofus, du jeu, de la map.";

        return "{"
            + "\"model\":\"" + model + "\","
            + "\"messages\":["
            +   "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
            +   "{\"role\":\"user\",\"content\":\"" + escapeJson(context) + "\"}"
            + "],"
            + "\"max_tokens\":80,"
            + "\"temperature\":0.85"
            + "}";
    }

    private static String buildCombatBody(String botName, BotPersonality personality, String context) {
        String systemPrompt = "Tu es le moteur tactique d'un bot Dofus 1.29. "
            + "Tu dois choisir si le bot peut raisonnablement battre le groupe. "
            + "Personnalite du bot : " + personalityDesc(personality) + ". "
            + "Reponds uniquement par FIGHT ou AVOID, puis une raison tres courte.";

        return "{"
            + "\"model\":\"" + model + "\","
            + "\"messages\":["
            +   "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
            +   "{\"role\":\"user\",\"content\":\"" + escapeJson(context) + "\"}"
            + "],"
            + "\"max_tokens\":40,"
            + "\"temperature\":0.2"
            + "}";
    }

    private static String parseContent(String json) {
        Matcher m = CONTENT_PATTERN.matcher(json);
        if(m.find()) {
            return m.group(1)
                    .replace("\\n", " ")
                    .replace("\\r", "")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }
        return null;
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static boolean isRateLimited(int botId) {
        Long last = lastCall.get(botId);
        return last != null && (System.currentTimeMillis() - last) < RATE_LIMIT_MS;
    }

    private static String personalityDesc(BotPersonality p) {
        switch(p) {
            case EXPLORER:  return "explorateur curieux, aime découvrir de nouvelles zones";
            case SOCIAL:    return "joueur social et amical, aime aider les autres";
            case MERCHANT:  return "marchand pragmatique, parle surtout de kamas et ressources";
            case WARRIOR:   return "guerrier déterminé, parle de combat et de farm";
            default:        return "joueur Dofus classique";
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
