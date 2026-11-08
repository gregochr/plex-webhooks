package gregochr.services.events;

import gregochr.support.FakePlaybackEventRepository;
import gregochr.support.LogCapture;
import gregochr.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static gregochr.services.events.PlaybackHistoryTest.event;
import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Playback Database Lifecycle Tests")
public class PlaybackDatabaseLifecycleTest {

    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private TaskScheduler scheduler;
    private FakePlaybackEventRepository repository;
    private PlaybackHistory history;
    private PlaybackService service;
    private PlaybackHistoryWriter writer;
    private AtomicInteger migrations;
    private RuntimeException migrationFailure;
    private PlaybackDatabaseLifecycle lifecycle;
    private LogCapture log;

    @BeforeEach
    public void setUp() {
        scheduler = mock(TaskScheduler.class, invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        });
        repository = new FakePlaybackEventRepository();
        history = new PlaybackHistory();
        service = mock(PlaybackService.class);
        writer = mock(PlaybackHistoryWriter.class);
        migrations = new AtomicInteger();
        lifecycle = new PlaybackDatabaseLifecycle(() -> {
            migrations.incrementAndGet();
            if (migrationFailure != null) {
                throw migrationFailure;
            }
        }, repository, history, service, writer, scheduler, FIXED);
        log = new LogCapture(PlaybackDatabaseLifecycle.class);
    }

    @AfterEach
    public void tearDown() {
        log.close();
    }

    /** What the lifecycle logged, without the "Scheduled ..." lines ScheduledTaskManager adds on the same logger. */
    private List<String> logged(List<String> messages) {
        return messages.stream().filter(m -> !m.startsWith("Scheduled ")).toList();
    }

    private void runMigrationTask() {
        scheduledTasks.get(0).run();
    }

    @DisplayName("start schedules the migration to run at once and then every 30 seconds")
    @Test
    public void startSchedulesMigrationAtOnce() {
        lifecycle.start();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT),
                eq(Duration.ofSeconds(30)));
        assertEquals(0, migrations.get());
    }

    @DisplayName("A successful migration raises the next id BEFORE marking the database ready, seeds the history, "
            + "schedules retention at once and stops the migration task")
    @Test
    public void successfulMigration() {
        repository.maxId = 1_710_503_999_999_999L;
        repository.stored = List.of(event(1_710_503_999_999_999L), event(1_710_503_999_999_998L));
        lifecycle.start();

        runMigrationTask();

        InOrder order = Mockito.inOrder(writer, service);
        order.verify(service).raiseNextId(1_710_504_000_000_000L);
        order.verify(writer).markReady();
        assertEquals(List.of(100), repository.newestLimits);
        assertEquals(List.of(1_710_503_999_999_999L, 1_710_503_999_999_998L),
                history.newestFirst(10).stream().map(PlaybackEvent::id).toList());
        assertEquals(2, futures.size());
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT),
                eq(Duration.ofHours(24)));
        verify(futures.get(0)).cancel(false);
        assertEquals(List.of("Playback database is ready; playback history is being saved"), logged(log.infos()));
    }

    @DisplayName("An empty table raises the next id to 1, which the clock-derived ids are always above")
    @Test
    public void emptyTable() {
        lifecycle.start();

        runMigrationTask();

        verify(service).raiseNextId(1);
    }

    @DisplayName("While the migration fails the database is not ready, one warning is logged and the task keeps "
            + "running; on recovery nothing more is warned")
    @Test
    public void failureLogsOnStateChangeOnly() {
        migrationFailure = new IllegalStateException("Unable to obtain connection");
        lifecycle.start();

        runMigrationTask();
        runMigrationTask();
        runMigrationTask();

        verify(writer, times(3)).markNotReady();
        verify(writer, never()).markReady();
        assertEquals(1, futures.size());
        verify(futures.get(0), never()).cancel(false);
        assertEquals(List.of("Playback database is not ready (IllegalStateException: Unable to obtain connection); "
                + "history stays in memory and the migration is retried every 30 s"), logged(log.warnings()));

        migrationFailure = null;
        runMigrationTask();

        assertEquals(1, log.warnings().size());
        assertEquals(List.of("Playback database is ready again; playback history is being saved"),
                logged(log.infos()).subList(1, logged(log.infos()).size()));
        verify(writer).markReady();
        verify(futures.get(0)).cancel(false);
    }

    @DisplayName("A failure after the migration, while reading max(id), is a failed attempt and is retried")
    @Test
    public void failureReadingMaxIdIsRetried() {
        repository.failure = new IllegalStateException("connection reset");
        lifecycle.start();

        runMigrationTask();

        verify(writer).markNotReady();
        verify(service, never()).raiseNextId(Mockito.anyLong());
        assertEquals(1, futures.size());
        verify(futures.get(0), never()).cancel(false);
    }

    @DisplayName("A table that vanishes after ready starts the migration again, at once")
    @Test
    public void missingTableRestartsMigration() {
        ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
        verify(writer).onSchemaMissing(handler.capture());
        lifecycle.start();
        runMigrationTask();

        handler.getValue().run();

        verify(scheduler, times(2)).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT),
                eq(Duration.ofSeconds(30)));
    }

    @DisplayName("Retention deletes everything older than exactly 90 days")
    @Test
    public void retentionCutoff() {
        lifecycle.start();
        runMigrationTask();

        scheduledTasks.get(1).run();

        assertEquals(List.of(Instant.parse("2023-12-16T12:00:00Z")), repository.cutoffs);
        assertEquals("Playback history retention deleted 3 events older than 90 days",
                log.infos().get(log.infos().size() - 1));
    }

    @DisplayName("A retention failure is logged and does not escape the scheduled task")
    @Test
    public void retentionFailureIsContained() {
        lifecycle.start();
        runMigrationTask();
        repository.failure = new IllegalStateException("db gone");

        scheduledTasks.get(1).run();

        assertTrue(log.warnings().contains(
                "Playback history retention failed (IllegalStateException); it runs again in 24 h"));
    }

    @DisplayName("stop cancels a migration that is still being retried")
    @Test
    public void stopCancelsMigration() {
        lifecycle.start();

        lifecycle.stop();

        verify(futures.get(0)).cancel(false);
    }

    @DisplayName("stop cancels the retention schedule")
    @Test
    public void stopCancelsRetention() {
        lifecycle.start();
        runMigrationTask();

        lifecycle.stop();

        verify(futures.get(1)).cancel(false);
    }

    @DisplayName("Nothing touches the database until start is called")
    @Test
    public void nothingBeforeStart() {
        verifyNoInteractions(service);
        assertEquals(0, migrations.get());
        assertEquals(List.of(), scheduledTasks);
    }
}
