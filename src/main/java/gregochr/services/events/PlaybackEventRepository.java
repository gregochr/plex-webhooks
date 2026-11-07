package gregochr.services.events;

import java.time.Instant;
import java.util.List;

/** Where recorded playback events are stored when a database is configured. Postgres is the only implementation. */
public interface PlaybackEventRepository {

    /**
     * Stores one event. An id that is already stored is left alone and logged, never overwritten.
     *
     * @throws org.springframework.dao.DataAccessException when the database cannot be reached or rejects the insert
     */
    void insert(PlaybackEvent event);

    /**
     * The newest stored events, ordered by {@code id} descending (never by {@code occurred_at}). A row that cannot be
     * read back, for example an unknown outcome, is skipped with a warning.
     */
    List<PlaybackEvent> newest(int limit);

    /** The largest stored id, or 0 when nothing is stored. */
    long maxId();

    /** Deletes events that occurred before {@code cutoff}; returns how many were deleted. */
    int deleteOlderThan(Instant cutoff);
}
