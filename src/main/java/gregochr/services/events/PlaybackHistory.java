package gregochr.services.events;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A bounded, in-memory record of the most recent media events. It is the live source for reads; Postgres (when
 * configured) is a write-behind copy that {@link #seed} loads back after a restart. Only
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

    /**
     * Merges stored events into the history in one locked step: the union by {@code id} (an event already in memory
     * wins over a stored one with the same id), ordered by {@code id}, keeping the newest {@link #CAPACITY}. It is
     * one call, never a read followed by a write, so an event recorded while the database was being read is kept.
     */
    public synchronized void seed(List<PlaybackEvent> stored) {
        Map<Long, PlaybackEvent> byId = new TreeMap<>();
        for (PlaybackEvent event : stored) {
            byId.put(event.id(), event);
        }
        for (PlaybackEvent event : events) {
            byId.put(event.id(), event);
        }
        List<PlaybackEvent> merged = new ArrayList<>(byId.values());
        events.clear();
        events.addAll(merged.subList(Math.max(0, merged.size() - CAPACITY), merged.size()));
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
