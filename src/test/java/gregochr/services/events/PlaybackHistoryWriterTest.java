package gregochr.services.events;

import gregochr.support.FakePlaybackEventRepository;
import gregochr.support.LogCapture;
import gregochr.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static gregochr.services.events.PlaybackHistoryTest.event;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Playback History Writer Tests")
public class PlaybackHistoryWriterTest {

    private FakePlaybackEventRepository repository;
    private MutableClock clock;
    private PlaybackHistoryWriter writer;
    private LogCapture log;

    @BeforeEach
    public void setUp() {
        repository = new FakePlaybackEventRepository();
        clock = new MutableClock();
        writer = new PlaybackHistoryWriter(repository, clock, 2, Duration.ofSeconds(5), () -> 0);
        writer.markReady();
        log = new LogCapture(PlaybackHistoryWriter.class);
    }

    @AfterEach
    public void tearDown() {
        log.close();
    }

    private static List<Long> ids(List<PlaybackEvent> events) {
        return events.stream().map(PlaybackEvent::id).toList();
    }

    private static BadSqlGrammarException tableMissing() {
        return new BadSqlGrammarException("insert", "INSERT INTO playback_event",
                new SQLException("relation \"playback_event\" does not exist", "42P01"));
    }

    @DisplayName("Production defaults: a queue of 1000, a 5 second drain and one warning a minute")
    @Test
    public void defaults() {
        assertEquals(1000, PlaybackHistoryWriter.QUEUE_CAPACITY);
        assertEquals(Duration.ofSeconds(5), PlaybackHistoryWriter.DRAIN_TIMEOUT);
        assertEquals(60_000, PlaybackHistoryWriter.WARN_INTERVAL_MS);
    }

    @DisplayName("Offered events are stored in the order they were offered")
    @Test
    public void storesInOrder() {
        writer.offer(event(1));
        writer.offer(event(2));

        assertEquals(2, writer.drainOnce());

        assertEquals(List.of(1L, 2L), ids(repository.inserted));
    }

    @DisplayName("offer returns without storing anything: the insert happens only when the queue is drained")
    @Test
    public void offerDoesNotStore() {
        writer.offer(event(1));

        assertEquals(List.of(), repository.inserted);
    }

    @DisplayName("Draining an empty queue stores nothing")
    @Test
    public void drainEmpty() {
        assertEquals(0, writer.drainOnce());
    }

    @DisplayName("Before the database is ready an event is dropped with a warning and never reaches the repository")
    @Test
    public void notReadyDrops() {
        writer.markNotReady();

        writer.offer(event(1));

        assertEquals(0, writer.drainOnce());
        assertEquals(List.of(), repository.inserted);
        assertEquals(List.of("Playback event not saved: the database is not ready "
                + "(0 similar warnings suppressed since the last)"), log.warnings());
    }

    @DisplayName("markReady and markNotReady switch whether events are accepted")
    @Test
    public void readyFlag() {
        assertTrue(writer.isReady());
        writer.markNotReady();
        assertFalse(writer.isReady());
        writer.markReady();
        writer.offer(event(1));

        assertEquals(1, writer.drainOnce());
    }

    @DisplayName("An event queued before the database went away is dropped at drain time, not inserted")
    @Test
    public void notReadyAtDrainTime() {
        writer.offer(event(1));
        writer.markNotReady();

        writer.drainOnce();

        assertEquals(List.of(), repository.inserted);
        assertEquals(1, log.warnings().size());
    }

    @DisplayName("A full queue drops the extra event with a warning and keeps the earlier ones")
    @Test
    public void fullQueueDrops() {
        writer.offer(event(1));
        writer.offer(event(2));
        writer.offer(event(3));

        writer.drainOnce();

        assertEquals(List.of(1L, 2L), ids(repository.inserted));
        assertEquals(List.of("Playback event not saved: the write queue is full "
                + "(0 similar warnings suppressed since the last)"), log.warnings());
    }

    @DisplayName("Warnings are rate-limited to one a minute and say how many were held back")
    @Test
    public void warningsAreRateLimited() {
        writer.markNotReady();

        writer.offer(event(1));
        writer.offer(event(2));
        clock.advance(Duration.ofMillis(59_999));
        writer.offer(event(3));
        assertEquals(1, log.warnings().size());

        clock.advance(Duration.ofMillis(1));
        writer.offer(event(4));

        assertEquals(2, log.warnings().size());
        assertEquals("Playback event not saved: the database is not ready "
                + "(2 similar warnings suppressed since the last)", log.warnings().get(1));
    }

    @DisplayName("A failed insert is logged and the next event is still stored")
    @Test
    public void insertFailureCarriesOn() {
        repository.failure = new DataAccessResourceFailureException("connection refused");
        writer.offer(event(1));
        writer.drainOnce();
        repository.failure = null;
        writer.offer(event(2));
        writer.drainOnce();

        assertEquals(List.of(2L), ids(repository.inserted));
        assertTrue(writer.isReady());
        assertEquals(List.of("Playback event not saved: the insert failed with DataAccessResourceFailureException "
                + "(0 similar warnings suppressed since the last)"), log.warnings());
    }

    @DisplayName("A missing table clears ready, runs the schema handler once and drops the events behind it")
    @Test
    public void missingTableRestartsMigration() {
        AtomicInteger handled = new AtomicInteger();
        writer.onSchemaMissing(() -> {
            assertFalse(writer.isReady());
            handled.incrementAndGet();
        });
        repository.failure = tableMissing();
        writer.offer(event(1));
        writer.offer(event(2));

        writer.drainOnce();

        assertFalse(writer.isReady());
        assertEquals(1, handled.get());
        assertEquals(List.of(), repository.inserted);
        assertEquals("Playback event 1 not saved: the playback_event table is missing; recreating the schema",
                log.warnings().get(0));
    }

    @DisplayName("A missing table is recognised when the SQLException is nested deeper in the cause chain")
    @Test
    public void missingTableNestedCause() {
        AtomicInteger handled = new AtomicInteger();
        writer.onSchemaMissing(handled::incrementAndGet);
        repository.failure = new IllegalStateException("wrapper", new RuntimeException("again", tableMissing()));
        writer.offer(event(1));

        writer.drainOnce();

        assertEquals(1, handled.get());
    }

    @DisplayName("Another SQL state leaves the writer ready and does not run the schema handler")
    @Test
    public void otherSqlStateIsNotAMissingTable() {
        AtomicInteger handled = new AtomicInteger();
        writer.onSchemaMissing(handled::incrementAndGet);
        repository.failure = new BadSqlGrammarException("insert", "INSERT", new SQLException("syntax", "42601"));
        writer.offer(event(1));

        writer.drainOnce();

        assertTrue(writer.isReady());
        assertEquals(0, handled.get());
    }

    @DisplayName("A missing table that has no SQLException anywhere in its causes is an ordinary failure")
    @Test
    public void causeChainWithoutSqlException() {
        AtomicInteger handled = new AtomicInteger();
        writer.onSchemaMissing(handled::incrementAndGet);
        repository.failure = new IllegalStateException("no sql here");
        writer.offer(event(1));

        writer.drainOnce();

        assertTrue(writer.isReady());
        assertEquals(0, handled.get());
    }

    @DisplayName("close stores what is queued, then offers after close are ignored without a warning")
    @Test
    public void closeDrainsThenIgnoresOffers() {
        writer.offer(event(1));
        writer.offer(event(2));

        writer.close();
        writer.offer(event(3));
        writer.drainOnce();

        assertEquals(List.of(1L, 2L), ids(repository.inserted));
        assertEquals(List.of(), log.warnings());
    }

    @DisplayName("close with no time to drain drops the queue and logs how many events were not saved")
    @Test
    public void closeWithoutTimeDropsAndCounts() {
        PlaybackHistoryWriter impatient = new PlaybackHistoryWriter(repository, clock, 5, Duration.ZERO, () -> 0);
        impatient.markReady();
        impatient.offer(event(1));
        impatient.offer(event(2));
        impatient.offer(event(3));

        impatient.close();

        assertEquals(List.of(), repository.inserted);
        assertEquals(List.of("Playback writer closed with 3 events not saved"), log.warnings());
    }

    @DisplayName("The writer thread is a daemon that stores offered events by itself and stops on close")
    @Test
    public void threadStoresAndStops() {
        PlaybackHistoryWriter threaded =
                new PlaybackHistoryWriter(repository, clock, 10, Duration.ofSeconds(5), System::nanoTime);
        threaded.markReady();
        // Other tests' Spring contexts (cached for the whole JVM) have their own writer thread with this name, so
        // find the one this test starts by what is new, not by its name alone.
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        threaded.start();
        try {
            threaded.offer(event(7));
            await().atMost(10, SECONDS).until(() -> ids(repository.inserted).equals(List.of(7L)));

            Thread writerThread = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> !before.contains(t) && t.getName().equals("cinelight-playback-writer"))
                    .findFirst().orElseThrow();
            assertTrue(writerThread.isDaemon());
            threaded.close();
            assertFalse(writerThread.isAlive());
        } finally {
            threaded.close();
        }
    }

    @DisplayName("close interrupted while joining the writer thread keeps the interrupt flag")
    @Test
    public void closeInterruptedWhileJoining() {
        PlaybackHistoryWriter threaded =
                new PlaybackHistoryWriter(repository, clock, 10, Duration.ofSeconds(5), System::nanoTime);
        threaded.start();
        Thread.currentThread().interrupt();
        try {
            threaded.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            threaded.close();
        }
    }

    /** A repository whose every insert moves the fake monotonic clock on, so the drain budget can be spent exactly. */
    private static final class SlowRepository extends FakePlaybackEventRepository {
        private final AtomicLong nanos;
        private final long insertNanos;

        SlowRepository(AtomicLong nanos, long insertNanos) {
            this.nanos = nanos;
            this.insertNanos = insertNanos;
        }

        @Override
        public void insert(PlaybackEvent event) {
            super.insert(event);
            nanos.addAndGet(insertNanos);
        }
    }

    @DisplayName("close stops storing the moment the 5 second budget is spent, not one event later")
    @Test
    public void closeStopsExactlyAtTheDeadline() {
        AtomicLong nanos = new AtomicLong();
        SlowRepository slow = new SlowRepository(nanos, Duration.ofSeconds(5).toNanos());
        PlaybackHistoryWriter budgeted =
                new PlaybackHistoryWriter(slow, clock, 5, Duration.ofSeconds(5), nanos::get);
        budgeted.markReady();
        budgeted.offer(event(1));
        budgeted.offer(event(2));

        budgeted.close();

        assertEquals(List.of(1L), ids(slow.inserted));
        assertEquals(List.of("Playback writer closed with 1 events not saved"), log.warnings());
    }

    @DisplayName("close stores everything while the budget lasts, and logs nothing")
    @Test
    public void closeWithinBudgetStoresAll() {
        AtomicLong nanos = new AtomicLong();
        SlowRepository slow = new SlowRepository(nanos, Duration.ofSeconds(1).toNanos());
        PlaybackHistoryWriter budgeted =
                new PlaybackHistoryWriter(slow, clock, 5, Duration.ofSeconds(5), nanos::get);
        budgeted.markReady();
        budgeted.offer(event(1));
        budgeted.offer(event(2));
        budgeted.offer(event(3));

        budgeted.close();

        assertEquals(List.of(1L, 2L, 3L), ids(slow.inserted));
        assertEquals(List.of(), log.warnings());
    }

    /** An exception whose cause chain loops back on itself, which Throwable.initCause alone cannot build. */
    private static final class LoopingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private transient Throwable next;

        @Override
        public synchronized Throwable getCause() {
            return next;
        }
    }

    @DisplayName("A cause chain that loops is not followed forever; it is an ordinary failure")
    @Test
    public void loopingCauseChainTerminates() {
        LoopingException first = new LoopingException();
        LoopingException second = new LoopingException();
        first.next = second;
        second.next = first;
        repository.failure = first;
        writer.offer(event(1));

        writer.drainOnce();

        assertTrue(writer.isReady());
    }

    @DisplayName("Interrupting the writer thread stops it")
    @Test
    public void interruptStopsTheThread() {
        PlaybackHistoryWriter threaded = new PlaybackHistoryWriter(repository, clock, 10, Duration.ofSeconds(5),
                System::nanoTime);
        threaded.start();
        try {
            Thread writerThread = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.isAlive() && t.getName().equals("cinelight-playback-writer"))
                    .findFirst().orElseThrow();

            writerThread.interrupt();

            await().atMost(10, SECONDS).until(() -> !writerThread.isAlive());
        } finally {
            threaded.close();
        }
    }
}
