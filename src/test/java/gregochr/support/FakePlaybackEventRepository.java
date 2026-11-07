package gregochr.support;

import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An in-memory {@link PlaybackEventRepository} that records every call and can be told to fail. */
public class FakePlaybackEventRepository implements PlaybackEventRepository {
    public final List<PlaybackEvent> inserted = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> newestLimits = Collections.synchronizedList(new ArrayList<>());
    public final List<Instant> cutoffs = Collections.synchronizedList(new ArrayList<>());
    public volatile RuntimeException failure;
    public volatile long maxId;
    public volatile List<PlaybackEvent> stored = List.of();

    @Override
    public void insert(PlaybackEvent event) {
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
        inserted.add(event);
    }

    @Override
    public List<PlaybackEvent> newest(int limit) {
        newestLimits.add(limit);
        return stored;
    }

    @Override
    public long maxId() {
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
        return maxId;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        cutoffs.add(cutoff);
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
        return 3;
    }
}
