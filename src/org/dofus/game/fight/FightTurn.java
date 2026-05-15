package org.dofus.game.fight;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Gestion du tour courant dans un combat.
 *
 * Chaque tour a une durée maximale. Si le joueur ne joue pas dans ce délai,
 * le tour passe automatiquement.
 */
public class FightTurn {
    public static final int TURN_DURATION_SEC = 30;

    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "fight-timer");
            thread.setDaemon(true);
            return thread;
        }
    });

    static {
        TIMER.setRemoveOnCancelPolicy(true);
        TIMER.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        TIMER.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    private final Fight fight;
    private Fighter currentFighter;
    private ScheduledFuture<?> timeoutTask;
    private int turnNumber = 0;
    private long turnStartMs = 0L;

    public FightTurn(Fight fight) {
        this.fight = fight;
    }

    public synchronized void startTurn(Fighter fighter) {
        cancelTimeout();
        this.currentFighter = fighter;
        this.turnNumber++;
        this.turnStartMs = System.currentTimeMillis();
        fighter.resetTurn();
        fighter.setTurnPassed(false);
        fight.broadcast("GTS" + fighter.getId() + "|" + (TURN_DURATION_SEC * 1000));
        fight.broadcastTurnState();
        timeoutTask = schedule(new Runnable() {
            public void run() {
                onTimeout();
            }
        }, TURN_DURATION_SEC, TimeUnit.SECONDS);
    }

    public synchronized long getRemainingMs() {
        if (turnStartMs == 0L) return TURN_DURATION_SEC * 1000L;
        long elapsed = System.currentTimeMillis() - turnStartMs;
        return Math.max(0L, TURN_DURATION_SEC * 1000L - elapsed);
    }

    public synchronized void endTurn() {
        cancelTimeout();
        Fighter ended = currentFighter;
        if (ended != null) {
            ended.setTurnPassed(true);
            // Séquence AncestraR : GTF (turn finish) → GTM (stats finales) → GTR (turn ready).
            // GTR signale au client "fin de tour OK, tu peux fermer l'UI tour et attendre GTS".
            fight.broadcast("GTF" + ended.getId());
            fight.broadcast(fight.buildTurnStatusPacket());
            fight.broadcast("GTR" + ended.getId());
        }
        currentFighter = null;
        fight.nextTurn();
    }

    private void onTimeout() {
        Fighter fighter;
        synchronized (this) {
            fighter = currentFighter;
        }
        if (fighter != null && !fighter.hasTurnPassed()) {
            endTurn();
        }
    }

    private synchronized void cancelTimeout() {
        ScheduledFuture<?> task = timeoutTask;
        timeoutTask = null;
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }

    public synchronized Fighter getCurrentFighter() {
        return currentFighter;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public synchronized ScheduledFuture<?> getTurnTimer() {
        return timeoutTask;
    }

    public static ScheduledFuture<?> schedule(final Runnable task, long delay, TimeUnit unit) {
        return TIMER.schedule(new Runnable() {
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, delay, unit);
    }

    public static void shutdown() {
        TIMER.shutdownNow();
    }
}
