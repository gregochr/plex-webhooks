package gregochr.services.events;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Stores recorded playback events through a {@link PlaybackEventRepository} on one daemon thread, so the Plex webhook
 * never waits on the database: {@link #offer} is a non-blocking queue insert that returns at once and never throws.
 *
 * <p>The queue is bounded; when it is full, or the database is not {@linkplain #markReady() ready}, the event is
 * dropped with a rate-limited warning. There is no backlog replay: the in-memory history still has the event, and
 * history matters less than the lights.
 */
public class PlaybackHistoryWriter {
    private static final Logger LOGGER = LogManager.getLogger(PlaybackHistoryWriter.class);

    static final int QUEUE_CAPACITY = 1000;
    static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);
    static final long WARN_INTERVAL_MS = 60_000;
    private static final long POLL_MS = 250;

    private final PlaybackEventRepository repository;
    private final Clock clock;
    private final Duration drainTimeout;
    private final LongSupplier nanoTime;
    private final BlockingQueue<PlaybackEvent> queue;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicLong lastWarnMillis = new AtomicLong();
    private final AtomicInteger suppressed = new AtomicInteger();
    private volatile boolean closed;
    private volatile Runnable schemaMissingHandler = () -> { };
    private Thread worker;

    public PlaybackHistoryWriter(PlaybackEventRepository repository, Clock clock) {
        this(repository, clock, QUEUE_CAPACITY, DRAIN_TIMEOUT, System::nanoTime);
    }

    /** Lets tests choose the queue size, the drain budget and the (monotonic) time source the drain budget uses. */
    PlaybackHistoryWriter(PlaybackEventRepository repository, Clock clock, int capacity, Duration drainTimeout,
            LongSupplier nanoTime) {
        this.repository = repository;
        this.clock = clock;
        this.drainTimeout = drainTimeout;
        this.nanoTime = nanoTime;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** Starts the writer thread. */
    @PostConstruct
    public synchronized void start() {
        worker = new Thread(this::run, "cinelight-playback-writer");
        worker.setDaemon(true);
        worker.start();
    }

    /** Called once the schema exists and the database accepts writes: events are stored from now on. */
    public void markReady() {
        ready.set(true);
    }

    /** Called when the database is unusable again: events are dropped until {@link #markReady()}. */
    public void markNotReady() {
        ready.set(false);
    }

    public boolean isReady() {
        return ready.get();
    }

    /** Sets what runs when an insert finds the table gone (SQL state 42P01), after the writer clears its ready flag. */
    public void onSchemaMissing(Runnable handler) {
        this.schemaMissingHandler = handler;
    }

    /** Queues an event for storing. Never blocks and never throws; after {@link #close()} it does nothing. */
    public void offer(PlaybackEvent event) {
        if (closed) {
            return;
        }
        if (!ready.get()) {
            warnRateLimited("the database is not ready");
            return;
        }
        if (!queue.offer(event)) {
            warnRateLimited("the write queue is full");
        }
    }

    /** Stores every event queued right now, on the calling thread. The seam unit tests drive instead of the thread. */
    int drainOnce() {
        int stored = 0;
        PlaybackEvent event = queue.poll();
        while (event != null) {
            store(event);
            stored++;
            event = queue.poll();
        }
        return stored;
    }

    /** Stops accepting events, then stores what is queued for at most {@code drainTimeout}, and logs what is left. */
    @PreDestroy
    public void close() {
        closed = true;
        long deadline = nanoTime.getAsLong() + drainTimeout.toNanos();
        Thread thread;
        synchronized (this) {
            thread = worker;
        }
        if (thread != null) {
            try {
                thread.join(drainTimeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        PlaybackEvent event = queue.poll();
        while (event != null && nanoTime.getAsLong() < deadline) {
            store(event);
            event = queue.poll();
        }
        int dropped = queue.size() + (event != null ? 1 : 0);
        if (dropped > 0) {
            LOGGER.warn("Playback writer closed with {} events not saved", dropped);
        }
    }

    private void run() {
        try {
            while (!closed) {
                PlaybackEvent event = queue.poll(POLL_MS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    store(event);
                }
            }
        } catch (InterruptedException e) {
            // Interrupted only to stop: the thread ends here, so there is no later code to pass the flag to.
            LOGGER.debug("Playback writer thread interrupted; stopping");
        }
    }

    private void store(PlaybackEvent event) {
        if (!ready.get()) {
            warnRateLimited("the database is not ready");
            return;
        }
        try {
            repository.insert(event);
        } catch (RuntimeException e) {
            if (SqlErrors.isMissingTable(e) && ready.compareAndSet(true, false)) {
                LOGGER.warn("Playback event {} not saved: the playback_event table is missing; recreating the schema",
                        event.id());
                schemaMissingHandler.run();
            } else {
                warnRateLimited("the insert failed with " + e.getClass().getSimpleName());
            }
        }
    }

    /** Logs at most one warning a minute, counting what it held back. */
    private void warnRateLimited(String reason) {
        long now = clock.millis();
        long last = lastWarnMillis.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnMillis.compareAndSet(last, now)) {
            LOGGER.warn("Playback event not saved: {} ({} similar warnings suppressed since the last)", reason,
                    suppressed.getAndSet(0));
        } else {
            suppressed.incrementAndGet();
        }
    }
}
