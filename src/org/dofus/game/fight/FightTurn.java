package org.dofus.game.fight;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Gestion du tour courant dans un combat.
 *
 * Chaque tour a une durée maximale (30 s par défaut).
 * Si le joueur ne joue pas dans ce délai, le tour passe automatiquement.
 *
 * Séquence d'un tour :
 *   1. {@link Fight} appelle {@code startTurn(fighter)} -> paquet {@code GTS}
 *   2. Le joueur envoie des actions {@code GA} (mouvement, sort, passer)
 *   3. Le joueur envoie {@code GKK} (fin de tour) OU le timer expire
 *   4. {@link Fight} appelle {@code endTurn()} -> paquet {@code GTF}
 */
public class FightTurn {

    /** Durée max d'un tour en secondes (configurable TODO). */
    public static final int TURN_DURATION_SEC = 30;

    private static final ScheduledExecutorService TIMER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fight-timer");
            t.setDaemon(true);
            return t;
        });

    private final Fight           fight;
    private       Fighter         currentFighter;
    private       ScheduledFuture<?> timeoutTask;
    private       int             turnNumber = 0;

    public FightTurn(Fight fight) {
        this.fight = fight;
    }

    // ── Cycle de tour ─────────────────────────────────────────────────────────

    /**
     * Démarre le tour du combattant donné.
     * Envoie {@code GTS{id|durationMs}} a tous les observateurs.
     */
    public void startTurn(Fighter fighter) {
        this.currentFighter = fighter;
        this.turnNumber++;
        fighter.resetTurn();
        fighter.setTurnPassed(false);

        // Format officiel 1.29 : id|dureeMs.
        fight.broadcast("GTS" + fighter.getId() + "|" + (TURN_DURATION_SEC * 1000));

        // Timer de timeout
        cancelTimeout();
        timeoutTask = TIMER.schedule(this::onTimeout, TURN_DURATION_SEC, TimeUnit.SECONDS);
    }

    /**
     * Termine le tour courant (demandé par le joueur ou le timer).
     * Envoie {@code GTF{id}} et demande au Fight de passer au suivant.
     */
    public void endTurn() {
        cancelTimeout();
        if(currentFighter != null) {
            currentFighter.setTurnPassed(true);
            fight.broadcast("GTF" + currentFighter.getId());
        }
        fight.nextTurn();
    }

    /** Timeout : le joueur n'a pas joué à temps. */
    private void onTimeout() {
        if(currentFighter != null && !currentFighter.hasTurnPassed()) {
            endTurn();
        }
    }

    private void cancelTimeout() {
        if(timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Fighter         getCurrentFighter() { return currentFighter; }
    public int             getTurnNumber()     { return turnNumber;     }
    public ScheduledFuture<?> getTurnTimer()   { return timeoutTask;    }

    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return TIMER.schedule(task, delay, unit);
    }

    /** Arrêt propre du timer (fin du serveur). */
    public static void shutdown() {
        TIMER.shutdown();
    }
}
