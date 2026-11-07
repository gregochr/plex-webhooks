package gregochr.services.events;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A bounded, in-memory record of the most recent media events, kept since boot and never persisted. Only
 * {@code media.*} events belong here; the caller filters, so that {@code library.*} or {@code admin.*} traffic
 * cannot push real plays out.
 */
@Component
public class PlaybackHistory {
    /** The number of events kept. */
    public static final int CAPACITY = 100;

    private final ArrayDeque<PlaybackEvent> events = new ArrayDeque<>(CAPACITY);

    /** Appends an event, dropping the oldest when the history is full. */
    public synchronized void add(PlaybackEvent event) {
        if (events.size() == CAPACITY) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    /** Returns up to {@code limit} events, newest first. */
    public synchronized List<PlaybackEvent> newestFirst(int limit) {
        List<PlaybackEvent> result = new ArrayList<>(Math.min(limit, events.size()));
        Iterator<PlaybackEvent> newestToOldest = events.descendingIterator();
        while (newestToOldest.hasNext() && result.size() < limit) {
            result.add(newestToOldest.next());
        }
        return result;
    }
}
