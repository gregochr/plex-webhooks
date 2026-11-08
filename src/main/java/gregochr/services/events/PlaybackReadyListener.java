package gregochr.services.events;

import gregochr.services.ScheduledTaskManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;

/**
 * Playback history's part of "the database is ready": raise the next id past every stored id, let the writer store,
 * seed the in-memory history from the table, and start the retention task (once now, then daily). Idempotent, so
 * {@link PlaybackDatabaseLifecycle} can retry it until it has succeeded once.
 */
public class PlaybackReadyListener implements DatabaseReadyListener {
    private static final Logger LOGGER = LogManager.getLogger(PlaybackReadyListener.class);

    static final long RETENTION_INTERVAL_MS = Duration.ofHours(24).toMillis();
    static final Duration RETENTION = Duration.ofDays(90);

    private final PlaybackEventRepository repository;
    private final PlaybackHistory history;
    private final PlaybackService playbackService;
    private final PlaybackHistoryWriter writer;
    private final Clock clock;
    private final ScheduledTaskManager retentionTask;

    public PlaybackReadyListener(PlaybackEventRepository repository, PlaybackHistory history,
            PlaybackService playbackService, PlaybackHistoryWriter writer, TaskScheduler taskScheduler, Clock clock) {
        this.repository = repository;
        this.history = history;
        this.playbackService = playbackService;
        this.writer = writer;
        this.clock = clock;
        this.retentionTask = new ScheduledTaskManager(taskScheduler, clock, RETENTION_INTERVAL_MS, 0,
                "playback history retention", this::deleteExpired, LOGGER);
    }

    @Override
    public String name() {
        return "playback history";
    }

    @Override
    public void onDatabaseReady() {
        try {
            // The next id is raised BEFORE the writer is ready: once it is ready a webhook's event is queued, and an
            // id at or below a stored one (the clock went back) would hit the primary key and be lost.
            playbackService.raiseNextId(repository.maxId() + 1);
            writer.markReady();
            history.seed(repository.newest(PlaybackHistory.CAPACITY));
        } catch (RuntimeException e) {
            writer.markNotReady();
            throw e;
        }
        retentionTask.start();
        LOGGER.info("Playback database is ready; playback history is being saved");
    }

    @Override
    public void onSchemaMissing(Runnable restartMigration) {
        writer.onSchemaMissing(restartMigration);
    }

    @Override
    public void stop() {
        retentionTask.shutdown();
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
