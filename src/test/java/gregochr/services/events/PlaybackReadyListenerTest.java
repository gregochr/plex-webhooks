package gregochr.services.events;

import gregochr.support.FakePlaybackEventRepository;
import gregochr.support.LogCapture;
import gregochr.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static gregochr.services.events.PlaybackHistoryTest.event;
import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Playback Ready Listener Tests")
public class PlaybackReadyListenerTest {

    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private TaskScheduler scheduler;
    private FakePlaybackEventRepository repository;
    private PlaybackHistory history;
    private PlaybackService service;
    private PlaybackHistoryWriter writer;
    private PlaybackReadyListener listener;
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
        listener = new PlaybackReadyListener(repository, history, service, writer, scheduler, FIXED);
        log = new LogCapture(PlaybackReadyListener.class);
    }

    @AfterEach
    public void tearDown() {
        log.close();
    }

    @DisplayName("The listener is named for the log")
    @Test
    public void name() {
        assertEquals("playback history", listener.name());
    }

    @DisplayName("It raises the next id BEFORE marking the database ready, then seeds the history and schedules "
            + "retention at once")
    @Test
    public void readySteps() {
        repository.maxId = 1_710_503_999_999_999L;
        repository.stored = List.of(event(1_710_503_999_999_999L), event(1_710_503_999_999_998L));

        listener.onDatabaseReady();

        InOrder order = Mockito.inOrder(writer, service);
        order.verify(service).raiseNextId(1_710_504_000_000_000L);
        order.verify(writer).markReady();
        assertEquals(List.of(100), repository.newestLimits);
        assertEquals(List.of(1_710_503_999_999_999L, 1_710_503_999_999_998L),
                history.newestFirst(10).stream().map(PlaybackEvent::id).toList());
        assertEquals(1, futures.size());
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT),
                eq(Duration.ofHours(24)));
        assertEquals(List.of("Playback database is ready; playback history is being saved"),
                log.infos().stream().filter(m -> !m.startsWith("Scheduled ")).toList());
    }

    @DisplayName("An empty table raises the next id to 1, which the clock-derived ids are always above")
    @Test
    public void emptyTable() {
        listener.onDatabaseReady();

        verify(service).raiseNextId(1);
    }

    @DisplayName("A failure reading max(id) marks the writer not ready, schedules nothing, and is rethrown to be "
            + "retried")
    @Test
    public void failureReadingMaxIdIsRethrown() {
        IllegalStateException failure = new IllegalStateException("connection reset");
        repository.failure = failure;

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> listener.onDatabaseReady());

        assertSame(failure, thrown);
        verify(writer).markNotReady();
        verify(writer, never()).markReady();
        verify(service, never()).raiseNextId(Mockito.anyLong());
        assertEquals(0, futures.size());
    }

    @DisplayName("Running it again after a success is harmless: the retention schedule is replaced, not doubled")
    @Test
    public void runningTwiceReplacesTheRetentionSchedule() {
        listener.onDatabaseReady();
        listener.onDatabaseReady();

        assertEquals(2, futures.size());
        verify(futures.get(0)).cancel(false);
        verify(futures.get(1), never()).cancel(false);
    }

    @DisplayName("The schema-missing hook is handed to the writer")
    @Test
    public void schemaMissingHandlerGoesToTheWriter() {
        Runnable restart = () -> { };

        listener.onSchemaMissing(restart);

        verify(writer).onSchemaMissing(restart);
    }

    @DisplayName("Retention deletes everything older than exactly 90 days")
    @Test
    public void retentionCutoff() {
        listener.onDatabaseReady();

        scheduledTasks.get(0).run();

        assertEquals(List.of(Instant.parse("2023-12-16T12:00:00Z")), repository.cutoffs);
        assertEquals("Playback history retention deleted 3 events older than 90 days",
                log.infos().get(log.infos().size() - 1));
    }

    @DisplayName("A retention failure is logged and does not escape the scheduled task")
    @Test
    public void retentionFailureIsContained() {
        listener.onDatabaseReady();
        repository.failure = new IllegalStateException("db gone");

        scheduledTasks.get(0).run();

        assertTrue(log.warnings().contains(
                "Playback history retention failed (IllegalStateException); it runs again in 24 h"));
    }

    @DisplayName("stop cancels the retention schedule")
    @Test
    public void stopCancelsRetention() {
        listener.onDatabaseReady();

        listener.stop();

        verify(futures.get(0)).cancel(false);
    }
}
