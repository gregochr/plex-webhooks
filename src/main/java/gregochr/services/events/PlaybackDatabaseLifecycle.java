package gregochr.services.events;

import gregochr.services.ScheduledTaskManager;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brings the database up without ever holding up startup or the lights. A task runs the migration at once and then
 * every 30 s, catching every {@link RuntimeException} (Flyway throws its own exception type, not a Spring
 * data-access one) and logging only when a step changes state. Once the migration has succeeded it runs the
 * {@link DatabaseReadyListener}s in their given order (playback history first, settings second), each in its own
 * try/catch: a listener that fails is retried on the next run, alone, and neither blocks nor undoes the others.
 * When the migration and every listener have succeeded once, the task stops itself.
 *
 * <p>If a table later vanishes (a listener reports SQL state 42P01, for example when {@code utils-pg} was rebuilt
 * empty) the schema is restored without a restart: every step is run again, after a normal 30 s wait, never at once.
 * A restart cannot be cancelled by a run already in progress. If a table is missing straight after a migration
 * that succeeded (Flyway's history says it exists, for example after a hand {@code DROP}), re-migrating cannot
 * help: it logs an error once and stops restarting, so there is no loop.
 */
public class PlaybackDatabaseLifecycle {
    private static final Logger LOGGER = LogManager.getLogger(PlaybackDatabaseLifecycle.class);

    static final long MIGRATION_RETRY_MS = 30_000;
    /** A table reported missing within this long of a restart's migration succeeding is not a rebuilt database. */
    static final long STRAIGHT_AFTER_MS = 5 * 60_000;

    private static final String MIGRATION = "migration";

    private final Runnable migrator;
    private final List<DatabaseReadyListener> listeners;
    private final Clock clock;
    private final ScheduledTaskManager migrationTask;
    private final Object lock = new Object();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final Set<DatabaseReadyListener> succeeded = Collections.newSetFromMap(new IdentityHashMap<>());
    private int generation;
    private boolean migrated;
    private boolean gaveUp;
    private long suspectUntilMillis;

    public PlaybackDatabaseLifecycle(Runnable migrator, List<DatabaseReadyListener> listeners,
            TaskScheduler taskScheduler, Clock clock) {
        this.migrator = migrator;
        this.listeners = List.copyOf(listeners);
        this.clock = clock;
        this.migrationTask = ScheduledTaskManager.selfStopping(taskScheduler, clock, MIGRATION_RETRY_MS, 0,
                "database migration", this::run, LOGGER);
        this.listeners.forEach(listener -> listener.onSchemaMissing(this::restartMigration));
    }

    /** Starts the migration task once the application is up. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        migrationTask.start();
    }

    /**
     * Runs every step again, after a normal 30 s wait. A run in progress is not cancelled and cannot stop the new
     * schedule: its results are discarded. Does nothing once the lifecycle has given up.
     */
    void restartMigration() {
        synchronized (lock) {
            if (gaveUp) {
                return;
            }
            if (clock.millis() < suspectUntilMillis) {
                giveUp("a table is missing again straight after a migration that succeeded");
                return;
            }
            generation++;
            migrated = false;
            succeeded.clear();
            migrationTask.startIn(MIGRATION_RETRY_MS);
        }
    }

    @PreDestroy
    public void stop() {
        migrationTask.shutdown();
        listeners.forEach(DatabaseReadyListener::stop);
    }

    private void run(ScheduledTaskManager self) {
        int runGeneration;
        boolean needsMigration;
        synchronized (lock) {
            if (gaveUp) {
                return;
            }
            runGeneration = generation;
            needsMigration = !migrated;
        }
        if (needsMigration && !migrate(runGeneration)) {
            return;
        }
        boolean allReady = true;
        for (DatabaseReadyListener listener : listeners) {
            synchronized (lock) {
                if (runGeneration != generation) {
                    return;
                }
                if (succeeded.contains(listener)) {
                    continue;
                }
            }
            try {
                listener.onDatabaseReady();
            } catch (RuntimeException e) {
                if (SqlErrors.isMissingTable(e)) {
                    synchronized (lock) {
                        if (runGeneration == generation) {
                            giveUp("the " + listener.name() + " table is missing straight after a migration that "
                                    + "succeeded");
                        }
                    }
                    return;
                }
                allReady = false;
                recordFailure(listener.name(), e);
                continue;
            }
            synchronized (lock) {
                if (runGeneration != generation) {
                    return;
                }
                succeeded.add(listener);
            }
            recordRecovered(listener.name());
        }
        synchronized (lock) {
            if (allReady && runGeneration == generation) {
                self.shutdown();
            }
        }
    }

    /**
     * Runs the migration; false when it failed. A run that a restart has overtaken records nothing (the caller's
     * next generation check ends it).
     */
    private boolean migrate(int runGeneration) {
        try {
            migrator.run();
        } catch (RuntimeException e) {
            recordFailure(MIGRATION, e);
            return false;
        }
        synchronized (lock) {
            if (runGeneration == generation) {
                migrated = true;
                if (runGeneration > 0) {
                    suspectUntilMillis = clock.millis() + STRAIGHT_AFTER_MS;
                }
            }
        }
        recordRecovered(MIGRATION);
        return true;
    }

    /** Called with the lock held: stops all retries for good and says why, once. */
    private void giveUp(String reason) {
        gaveUp = true;
        migrationTask.shutdown();
        LOGGER.error("Database setup stopped: {}. Re-running the migration cannot fix this; it needs a manual fix "
                + "(or 'flyway repair'), and the database features stay off until the application is restarted.",
                reason);
    }

    private void recordFailure(String step, RuntimeException e) {
        if (failing.add(step)) {
            LOGGER.warn("Database step '{}' is not ready ({}: {}); it is retried every {} s", step,
                    e.getClass().getSimpleName(), e.getMessage(), MIGRATION_RETRY_MS / 1000);
        }
    }

    private void recordRecovered(String step) {
        if (failing.remove(step)) {
            LOGGER.info("Database step '{}' is ready again", step);
        }
    }
}
