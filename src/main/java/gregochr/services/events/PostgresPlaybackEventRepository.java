package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Stores playback events in the {@code playback_event} table (see {@code V1__playback_history.sql}). */
public class PostgresPlaybackEventRepository implements PlaybackEventRepository {
    private static final Logger LOGGER = LogManager.getLogger(PostgresPlaybackEventRepository.class);

    private static final String INSERT = """
            INSERT INTO playback_event (id, occurred_at, event, media_type, title, grandparent_title, parent_index,
                idx, year, player, outcome, scene_id, source, payload, command_title, duration)
            VALUES (:id, :occurredAt, :event, :mediaType, :title, :grandparentTitle, :parentIndex, :idx, :year,
                :player, :outcome, :sceneId, :source, cast(:payload as jsonb), :commandTitle, :duration)
            ON CONFLICT (id) DO NOTHING""";

    private static final String NEWEST = """
            SELECT id, occurred_at, event, media_type, title, grandparent_title, parent_index, idx, year, player,
                outcome, scene_id, source, payload::text AS payload, command_title, duration
            FROM playback_event ORDER BY id DESC LIMIT :limit""";

    private final JdbcClient jdbc;

    public PostgresPlaybackEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PlaybackEvent event) {
        int rows = jdbc.sql(INSERT)
                .param("id", event.id())
                .param("occurredAt", OffsetDateTime.ofInstant(Instant.parse(event.timestamp()), ZoneOffset.UTC))
                .param("event", event.event())
                .param("mediaType", event.mediaType(), Types.VARCHAR)
                .param("title", event.title(), Types.VARCHAR)
                .param("grandparentTitle", event.grandparentTitle(), Types.VARCHAR)
                .param("parentIndex", event.parentIndex(), Types.INTEGER)
                .param("idx", event.index(), Types.INTEGER)
                .param("year", event.year(), Types.INTEGER)
                .param("player", event.player(), Types.VARCHAR)
                .param("outcome", event.outcome().name())
                .param("sceneId", event.sceneId(), Types.VARCHAR)
                .param("source", event.source())
                .param("payload", event.payload(), Types.VARCHAR)
                .param("commandTitle", event.command().title(), Types.VARCHAR)
                .param("duration", event.command().duration())
                .update();
        if (rows == 0) {
            LOGGER.warn("[ Playback event id {} is already stored; the new event was not saved (id collision) ]",
                    event.id());
        }
    }

    @Override
    public List<PlaybackEvent> newest(int limit) {
        return jdbc.sql(NEWEST).param("limit", limit)
                .query(PostgresPlaybackEventRepository::toEvent)
                .list().stream().filter(Objects::nonNull).toList();
    }

    @Override
    public long maxId() {
        return jdbc.sql("SELECT coalesce(max(id), 0) FROM playback_event").query(Long.class).single();
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.sql("DELETE FROM playback_event WHERE occurred_at < :cutoff")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }

    /** Rebuilds an event, including the legacy {@code command}; null (skipped) when the outcome is unknown. */
    private static PlaybackEvent toEvent(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        String outcomeName = rs.getString("outcome");
        PlexOutcome.Kind outcome;
        try {
            outcome = PlexOutcome.Kind.valueOf(outcomeName);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[ Skipping stored playback event {}: unknown outcome '{}' ]", id, outcomeName);
            return null;
        }
        String event = rs.getString("event");
        return new PlaybackEvent(
                id,
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant().toString(),
                event,
                rs.getString("media_type"),
                rs.getString("title"),
                rs.getString("grandparent_title"),
                (Integer) rs.getObject("parent_index"),
                (Integer) rs.getObject("idx"),
                (Integer) rs.getObject("year"),
                rs.getString("player"),
                outcome,
                rs.getString("scene_id"),
                rs.getString("source"),
                rs.getString("payload"),
                new PlaybackEvent.Command(event, rs.getString("command_title"), rs.getInt("duration")));
    }
}
