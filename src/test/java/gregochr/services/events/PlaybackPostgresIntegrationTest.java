package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import gregochr.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A database that already holds history from a previous run: the app seeds its ring from it, never reuses a stored
 * id (even one from a clock far in the future), stores new events, and deletes rows older than 90 days.
 */
@DisplayName("Playback Postgres Integration Tests")
public class PlaybackPostgresIntegrationTest extends PostgresSpringTestBase {

    private static final long OLD_ID = 1_000;
    private static final long RECENT_ID = 1_710_503_000_000_000L;
    /** An id minted by a clock set to the year 2100. */
    private static final long FUTURE_ID = 4_102_444_800_000_000_000L;
    private static final String DATABASE = EmbeddedPostgresSupport.createDatabase();
    private static final JdbcClient DB = EmbeddedPostgresSupport.client(DATABASE);

    static {
        EmbeddedPostgresSupport.migrate(DATABASE);
        PostgresPlaybackEventRepository repository = new PostgresPlaybackEventRepository(DB);
        Instant now = Instant.now();
        repository.insert(stored(OLD_ID, now.minus(Duration.ofDays(91))));
        repository.insert(stored(RECENT_ID, now.minus(Duration.ofHours(1))));
        repository.insert(stored(FUTURE_ID, now.minus(Duration.ofMinutes(5))));
        DB.sql("INSERT INTO playback_event (id, occurred_at, event, outcome, source) "
                + "VALUES (2000, now(), 'media.play', 'FROM_THE_FUTURE', 'plex')").update();
    }

    @Autowired
    private PlaybackHistoryWriter writer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
        // What docker-compose.yml sets. The group must not be called "db": Spring Boot refuses a group named like
        // the db health contributor, and that would stop the app starting.
        registry.add("management.endpoint.health.group.database.include", () -> "db");
    }

    private static PlaybackEvent stored(long id, Instant at) {
        return new PlaybackEvent(id, at.toString(), "media.play", "movie", "Earlier", null, null, null, null, null,
                PlexOutcome.Kind.SCENE_APPLIED, "start-scene", "plex", "{}");
    }

    private void awaitSeeded() {
        await().atMost(20, SECONDS).until(() -> history.newestFirst(100).stream()
                .anyMatch(e -> e.id() == FUTURE_ID));
    }

    @DisplayName("After a restart the ring holds the stored events, newest id first, and skips an unknown outcome")
    @Test
    public void seedsHistoryFromTheTable() {
        awaitSeeded();

        List<Long> ids = history.newestFirst(100).stream().map(PlaybackEvent::id).toList();
        assertTrue(ids.containsAll(List.of(FUTURE_ID, RECENT_ID)), ids.toString());
        assertTrue(!ids.contains(2000L), "the row with an unknown outcome must be skipped: " + ids);
        assertTrue(ids.indexOf(FUTURE_ID) < ids.indexOf(RECENT_ID), ids.toString());
    }

    @DisplayName("New ids are above every stored id, even one minted by a clock set far in the future")
    @Test
    public void newIdsAreAboveStoredIds() throws Exception {
        awaitSeeded();

        PlaybackEvent event = play();

        assertEquals(FUTURE_ID + 1, event.id());
    }

    @DisplayName("A Plex play is stored in the table once the database is up, with its scene")
    @Test
    public void historyLandsInTheTable() throws Exception {
        await().atMost(20, SECONDS).until(writer::isReady);

        PlaybackEvent event = play();

        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", event.id()).query(Long.class).single() == 1);
        Map<String, Object> row = DB.sql("SELECT outcome, scene_id, title, source, year, player "
                + "FROM playback_event WHERE id = :id").param("id", event.id()).query().singleRow();
        assertEquals(Map.of("outcome", "SCENE_APPLIED", "scene_id", "start-scene", "title", "Heat",
                "source", "plex", "year", 1995, "player", "Den"), row);
    }

    @DisplayName("The database health group is UP while the container group, which Docker reads, stays UP too")
    @Test
    public void databaseHealthGroupIsUp() throws Exception {
        await().atMost(20, SECONDS).until(writer::isReady);

        mockMvc.perform(get("/actuator/health/database")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
        mockMvc.perform(get("/actuator/health/container")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @DisplayName("Retention runs when the database becomes ready: rows older than 90 days go, recent ones stay")
    @Test
    public void retentionDeletesOldRows() {
        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", OLD_ID).query(Long.class).single() == 0);

        assertEquals(1L, DB.sql("SELECT count(*) FROM playback_event WHERE id = :id").param("id", RECENT_ID)
                .query(Long.class).single());
    }
}
