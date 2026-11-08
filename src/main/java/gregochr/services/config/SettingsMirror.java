package gregochr.services.config;

import gregochr.services.ScheduledTaskManager;
import gregochr.services.events.DatabaseReadyListener;
import gregochr.services.events.SqlErrors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a copy of the settings file in Postgres, with a log of what changed. The file stays the only thing the app
 * starts from and reads; the database never feeds back into it.
 *
 * <p>It does not copy individual saves. It reconciles the <em>whole file</em> into the database whenever something
 * may have changed, so the database always equals the file and the order of saves cannot matter. A dashboard save
 * ({@link PropertiesUpdatedEvent}) and the database becoming ready each set a {@code dirty} flag; a task checks it
 * every 5 s and, when the database is ready and the flag is set, clears it, takes the keys saved since the last
 * reconcile, reads the file's text and applies {@link SettingsDiff} in one transaction. A save that lands during a
 * reconcile sets the flag again, so nothing is lost. On a database error the saved keys are put back and the flag
 * is set, so the next run tries again.
 *
 * <p>Credentials and {@code spring.datasource.*} keys never reach the database (see {@link SettingsDiff}); if a key
 * becomes excluded later, its {@code setting} row and the values in its {@code setting_change} rows are purged.
 */
public class SettingsMirror implements DatabaseReadyListener {
    private static final Logger LOGGER = LogManager.getLogger(SettingsMirror.class);

    static final long CHECK_INTERVAL_MS = 5_000;
    static final long WARN_INTERVAL_MS = 60_000;

    private final SettingsRepository repository;
    private final PropertiesRepository properties;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ScheduledTaskManager task;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Set<String> pendingKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastWarnMillis = new AtomicLong();
    private final AtomicInteger suppressed = new AtomicInteger();
    private volatile boolean ready;
    private volatile Runnable schemaMissingHandler = () -> { };

    public SettingsMirror(SettingsRepository repository, PropertiesRepository properties,
            TransactionTemplate transaction, TaskScheduler taskScheduler, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
        this.task = new ScheduledTaskManager(taskScheduler, clock, CHECK_INTERVAL_MS, 0, "settings mirror",
                this::reconcile, LOGGER);
    }

    @Override
    public String name() {
        return "settings mirror";
    }

    /** Marks the mirror ready and dirty (the file may differ from the table) and starts the 5 s check. */
    @Override
    public void onDatabaseReady() {
        ready = true;
        dirty.set(true);
        task.start();
        LOGGER.info("Settings mirror is ready; the settings file is being copied to the database");
    }

    @Override
    public void onSchemaMissing(Runnable restartMigration) {
        this.schemaMissingHandler = restartMigration;
    }

    @Override
    public void stop() {
        task.shutdown();
    }

    /** A dashboard save: remember which keys, then ask for a reconcile. */
    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        pendingKeys.addAll(event.getUpdatedProperties().keySet());
        dirty.set(true);
    }

    /** The 5 s check: does nothing unless the database is ready and something may have changed. */
    void reconcile() {
        if (!ready || !dirty.compareAndSet(true, false)) {
            return;
        }
        Set<String> saved = takePendingKeys();
        try {
            Map<String, String> file = properties.readFileEntries();
            Instant now = clock.instant();
            transaction.executeWithoutResult(status -> apply(file, saved, now));
        } catch (RuntimeException e) {
            pendingKeys.addAll(saved);
            dirty.set(true);
            if (SqlErrors.isMissingTable(e)) {
                ready = false;
                LOGGER.warn("Settings not copied: a settings table is missing; the schema is being recreated");
                schemaMissingHandler.run();
            } else {
                warnRateLimited(e);
            }
        }
    }

    private void apply(Map<String, String> file, Set<String> saved, Instant now) {
        Map<String, String> stored = repository.loadAll();
        List<SettingChange> changes = SettingsDiff.compute(stored, file, saved, stored.isEmpty());
        for (SettingChange change : changes) {
            if (change.newValue() == null) {
                repository.delete(change.key());
            } else {
                repository.upsert(change.key(), change.newValue(), now);
            }
            repository.recordChange(change, now);
        }
        Set<String> leaking = new HashSet<>(repository.keysWithRecordedValues());
        leaking.removeIf(key -> !SettingsDiff.isExcluded(key));
        if (!leaking.isEmpty()) {
            repository.purgeRecordedValues(leaking);
        }
    }

    private Set<String> takePendingKeys() {
        Set<String> taken = new HashSet<>();
        for (String key : pendingKeys) {
            if (pendingKeys.remove(key)) {
                taken.add(key);
            }
        }
        return taken;
    }

    /** Logs at most one warning a minute, counting what it held back. */
    private void warnRateLimited(RuntimeException error) {
        long now = clock.millis();
        long last = lastWarnMillis.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnMillis.compareAndSet(last, now)) {
            LOGGER.warn("Settings not copied to the database ({}: {}); it is retried every {} s ({} similar "
                    + "warnings suppressed since the last)", error.getClass().getSimpleName(), error.getMessage(),
                    CHECK_INTERVAL_MS / 1000, suppressed.getAndSet(0));
        } else {
            suppressed.incrementAndGet();
        }
    }
}
