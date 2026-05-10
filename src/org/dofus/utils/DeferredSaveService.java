package org.dofus.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.dofus.database.objects.CharactersData;
import org.dofus.objects.actors.Characters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coalesce stat boost DB writes: first boost starts a 3-min timer;
 * subsequent boosts reset it. On disconnect, RolePlayHandler still
 * calls CharactersData.update() immediately — this timer is purely
 * an optimisation to avoid a write on every single boost click.
 */
public class DeferredSaveService {

	private static final Logger logger = LoggerFactory.getLogger(DeferredSaveService.class);

	private static final long DELAY_SECONDS = 180;

	private static final ScheduledExecutorService executor =
		Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "deferred-save");
			t.setDaemon(true);
			return t;
		});

	private static final ConcurrentMap<Integer, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

	public static void schedule(Characters character) {
		ScheduledFuture<?> old = pending.remove(character.getId());
		if(old != null) old.cancel(false);

		ScheduledFuture<?> future = executor.schedule(() -> {
			pending.remove(character.getId());
			if(character.isConnected()) {
				CharactersData.update(character);
				logger.debug("Deferred save for {}", character.getName());
			}
		}, DELAY_SECONDS, TimeUnit.SECONDS);

		pending.put(character.getId(), future);
	}

	/** Cancel any pending save for a character (called on disconnect — immediate save takes over). */
	public static void cancel(int characterId) {
		ScheduledFuture<?> old = pending.remove(characterId);
		if(old != null) old.cancel(false);
	}

	public static void shutdown() {
		executor.shutdown();
	}
}
