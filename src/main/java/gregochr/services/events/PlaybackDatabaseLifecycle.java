package gregochr.services.events;

import gregochr.services.ScheduledTaskManager;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Brings the playback database up without ever holding up startup or the lights. A task runs the migration at once
 * and then every 30 s until it succeeds, catching every {@link RuntimeException} (Flyway throws its own exception
 * type, not a Spring data-access one) and logging only when the state changes. When it succeeds the database is
 * ready: the writer starts storing, the next id is raised past every stored id, the in-memory history is seeded
 * from the table, retention runs once and then daily, and the migration task stops itself.
 *
 * <p>If the table later vanishes (the writer reports SQL state 42P01, for example when {@code utils-pg} was rebuilt
 * empty) the migration task is started again, so the schema comes back without a restart.
 */
public class PlaybackDatabaseLifecycle {
    private static final Logger LOGGER = LogManager.getLogger(PlaybackDatabaseLifecycle.class);

    static final long MIGRATION_RETRY_MS = 30_000;
    static final long RETENTION_INTERVAL_MS = Duration.ofHours(24).toMillis();
    static final Duration RETENTION = Duration.ofDays(90);

    private final Runnable migrator;
    private final PlaybackEventRepository repository;
    private final PlaybackHistory history;
    private final PlaybackService playbackService;
    private final PlaybackHistoryWriter writer;
    private final Clock clock;
    private final ScheduledTaskManager migrationTask;
    private final ScheduledTaskManager retentionTask;
    private final AtomicBoolean failing = new AtomicBoolean();

    public PlaybackDatabaseLifecycle(Runnable migrator, PlaybackEventRepository repository, PlaybackHistory history,
            PlaybackService playbackService, PlaybackHistoryWriter writer, TaskScheduler taskScheduler, Clock clock) {
        this.migrator = migrator;
        this.repository = repository;
        this.history = history;
        this.playbackService = playbackService;
        this.writer = writer;
        this.clock = clock;
        this.migrationTask = ScheduledTaskManager.selfStopping(taskScheduler, clock, MIGRATION_RETRY_MS, 0,
                "playback database migration", this::migrate, LOGGER);
        this.retentionTask = new ScheduledTaskManager(taskScheduler, clock, RETENTION_INTERVAL_MS, 0,
                "playback history retention", this::deleteExpired, LOGGER);
        writer.onSchemaMissing(this::restartMigration);
    }

    /** Starts the migration task once the application is up. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        migrationTask.start();
    }

    /** Runs the migration again at once, then every 30 s until it succeeds. */
    void restartMigration() {
        migrationTask.start();
    }

    @PreDestroy
    public void stop() {
        migrationTask.shutdown();
        retentionTask.shutdown();
    }

    private void migrate(ScheduledTaskManager self) {
        try {
            migrator.run();
            // The next id is raised BEFORE the writer is ready: once it is ready a webhook's event is queued, and an
            // id at or below a stored one (the clock went back) would hit the primary key and be lost.
            playbackService.raiseNextId(repository.maxId() + 1);
            writer.markReady();
            history.seed(repository.newest(PlaybackHistory.CAPACITY));
        } catch (RuntimeException e) {
            writer.markNotReady();
            if (failing.compareAndSet(false, true)) {
                LOGGER.warn("Playback database is not ready ({}: {}); history stays in memory and the migration is "
                        + "retried every {} s", e.getClass().getSimpleName(), e.getMessage(),
                        MIGRATION_RETRY_MS / 1000);
            }
            return;
        }
        if (failing.getAndSet(false)) {
            LOGGER.info("Playback database is ready again; playback history is being saved");
        } else {
            LOGGER.info("Playback database is ready; playback history is being saved");
        }
        retentionTask.start();
        self.shutdown();
    }

    private void deleteExpired() {
        try {
            int deleted = repository.deleteOlderThan(clock.instant().minus(RETENTION));
            LOGGER.info("Playback history retention deleted {} events older than {} days", deleted,
                    RETENTION.toDays());
        } catch (RuntimeException e) {
            LOGGER.warn("Playback history retention failed ({}); it runs again in 24 h", e.getClass().getSimpleName());
        }
    }
}
