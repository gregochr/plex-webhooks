package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import gregochr.support.EmbeddedPostgresSupport;
import gregochr.support.LogCapture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real SQL against a real PostgreSQL 16 with the application's real Flyway migration. */
@Tag("integration")
@Tag("postgres")
@DisplayName("Postgres Playback Event Repository Tests")
public class PostgresPlaybackEventRepositoryTest {

    private static String database;
    private static JdbcClient jdbc;
    private static PostgresPlaybackEventRepository repository;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void createSchema() {
        database = EmbeddedPostgresSupport.createDatabase();
        EmbeddedPostgresSupport.migrate(database);
        jdbc = EmbeddedPostgresSupport.client(database);
        repository = new PostgresPlaybackEventRepository(jdbc);
    }

    @BeforeEach
    void emptyTable() {
        jdbc.sql("DELETE FROM playback_event").update();
    }

    private static PlaybackEvent event(long id, String timestamp) {
        return new PlaybackEvent(id, timestamp, "media.play", "movie", "Heat", null, null, null, null, null,
                PlexOutcome.Kind.IGNORED_RULE_OFF, null, "plex", "{}",
                new PlaybackEvent.Command("media.play", "Heat", 0));
    }

    @DisplayName("The tests run on PostgreSQL 16, the version utils-pg runs")
    @Test
    public void runsOnPostgres16() {
        assertTrue(EmbeddedPostgresSupport.serverVersion().startsWith("16."),
                EmbeddedPostgresSupport.serverVersion());
    }

    @DisplayName("The migration creates the playback_event table with every column and the occurred_at index")
    @Test
    public void schema() {
        List<String> columns = jdbc.sql("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'playback_event' ORDER BY ordinal_position").query(String.class).list();
        List<String> indexes = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE tablename = 'playback_event'")
                .query(String.class).list();

        assertEquals(List.of("id", "occurred_at", "event", "media_type", "title", "grandparent_title",
                "parent_index", "idx", "year", "player", "outcome", "scene_id", "source", "payload",
                "command_title", "duration"), columns);
        assertTrue(indexes.stream().anyMatch(i -> i.contains("(occurred_at)")), indexes.toString());
    }

    @DisplayName("An event with every field set is stored and read back identical, down to the legacy command")
    @Test
    public void roundTripEveryField() {
        PlaybackEvent full = new PlaybackEvent(1_710_504_000_000_001L, "2024-03-15T12:00:00.123456Z", "media.play",
                "episode", "Cold Open", "Slow Horses", 4, 2, 2025, "Living Room TV",
                PlexOutcome.Kind.SCENE_APPLIED, "start-scene", "plex",
                "{\"event\": \"media.play\", \"Metadata\": {\"title\": \"Cold Open\"}}",
                new PlaybackEvent.Command("media.play", "Cold Open", 2700000));

        repository.insert(full);

        assertEquals(List.of(full), repository.newest(10));
    }

    @DisplayName("Null columns and a legacy command title of empty string survive the round trip")
    @Test
    public void roundTripNullsAndEmptyCommandTitle() {
        PlaybackEvent sparse = new PlaybackEvent(7, "2024-03-15T12:00:00Z", "media.play", null, null, null, null,
                null, null, null, PlexOutcome.Kind.IGNORED_UNSUPPORTED, null, "debug", "{}",
                new PlaybackEvent.Command("media.play", "", 0));

        repository.insert(sparse);

        assertEquals(List.of(sparse), repository.newest(10));
    }

    @DisplayName("The payload is stored as jsonb and read back as equal JSON")
    @Test
    public void payloadIsJsonb() {
        String payload = "{\"b\":1,\"a\":{\"x\":[1,2]}}";
        PlaybackEvent withPayload = new PlaybackEvent(8, "2024-03-15T12:00:00Z", "media.play", "movie", "Heat",
                null, null, null, null, null, PlexOutcome.Kind.IGNORED_RULE_OFF, null, "plex", payload,
                new PlaybackEvent.Command("media.play", "Heat", 0));

        repository.insert(withPayload);

        assertEquals("object", jdbc.sql("SELECT jsonb_typeof(payload) FROM playback_event WHERE id = 8")
                .query(String.class).single());
        assertEquals(JSON.readTree(payload), JSON.readTree(repository.newest(1).get(0).payload()));
    }

    @DisplayName("newest orders by id, not by occurred_at, and honours the limit")
    @Test
    public void newestOrdersById() {
        repository.insert(event(5, "2024-03-15T12:00:03Z"));
        repository.insert(event(9, "2024-03-15T12:00:01Z"));
        repository.insert(event(3, "2024-03-15T12:00:09Z"));

        assertEquals(List.of(9L, 5L, 3L), repository.newest(10).stream().map(PlaybackEvent::id).toList());
        assertEquals(List.of(9L, 5L), repository.newest(2).stream().map(PlaybackEvent::id).toList());
    }

    @DisplayName("Inserting an id that is already stored leaves the original row and logs a collision warning")
    @Test
    public void duplicateIdIsLoggedNotOverwritten() {
        repository.insert(event(4, "2024-03-15T12:00:00Z"));
        PlaybackEvent rival = new PlaybackEvent(4, "2024-03-15T12:00:01Z", "media.stop", "movie", "Other", null,
                null, null, null, null, PlexOutcome.Kind.FAILED, null, "plex", "{}",
                new PlaybackEvent.Command("media.stop", "Other", 0));

        try (LogCapture log = new LogCapture(PostgresPlaybackEventRepository.class)) {
            repository.insert(rival);

            assertEquals(List.of("[ Playback event id 4 is already stored; the new event was not saved "
                    + "(id collision) ]"), log.warnings());
        }
        assertEquals(List.of(event(4, "2024-03-15T12:00:00Z")), repository.newest(10));
    }

    @DisplayName("A stored row whose outcome is not a known kind is skipped with a warning, the rest are returned")
    @Test
    public void unknownOutcomeIsSkipped() {
        repository.insert(event(1, "2024-03-15T12:00:00Z"));
        repository.insert(event(3, "2024-03-15T12:00:00Z"));
        jdbc.sql("INSERT INTO playback_event (id, occurred_at, event, outcome, source) "
                + "VALUES (2, now(), 'media.play', 'FROM_THE_FUTURE', 'plex')").update();

        try (LogCapture log = new LogCapture(PostgresPlaybackEventRepository.class)) {
            List<PlaybackEvent> newest = repository.newest(10);

            assertEquals(List.of(3L, 1L), newest.stream().map(PlaybackEvent::id).toList());
            assertEquals(List.of("[ Skipping stored playback event 2: unknown outcome 'FROM_THE_FUTURE' ]"),
                    log.warnings());
        }
    }

    @DisplayName("maxId is 0 for an empty table and the largest id otherwise")
    @Test
    public void maxId() {
        assertEquals(0, repository.maxId());

        repository.insert(event(12, "2024-03-15T12:00:00Z"));
        repository.insert(event(4_102_444_800_000_000_000L, "2024-03-15T12:00:00Z"));
        repository.insert(event(30, "2024-03-15T12:00:00Z"));

        assertEquals(4_102_444_800_000_000_000L, repository.maxId());
    }

    @DisplayName("deleteOlderThan deletes strictly older rows: one microsecond older goes, exactly at the cutoff stays")
    @Test
    public void retentionBoundary() {
        repository.insert(event(1, "2023-12-16T11:59:59.999999Z"));
        repository.insert(event(2, "2023-12-16T12:00:00Z"));
        repository.insert(event(3, "2023-12-16T12:00:00.000001Z"));

        int deleted = repository.deleteOlderThan(Instant.parse("2023-12-16T12:00:00Z"));

        assertEquals(1, deleted);
        assertEquals(List.of(3L, 2L), repository.newest(10).stream().map(PlaybackEvent::id).toList());
    }

    @DisplayName("Inserting before the schema exists fails with SQL state 42P01, which the writer recovers from")
    @Test
    public void missingTableIsSqlState42P01() {
        String empty = EmbeddedPostgresSupport.createDatabase();
        PostgresPlaybackEventRepository noSchema =
                new PostgresPlaybackEventRepository(EmbeddedPostgresSupport.client(empty));

        BadSqlGrammarException thrown = assertThrows(BadSqlGrammarException.class,
                () -> noSchema.insert(event(1, "2024-03-15T12:00:00Z")));

        SQLException cause = thrown.getSQLException();
        assertEquals("42P01", cause.getSQLState());
    }
}
