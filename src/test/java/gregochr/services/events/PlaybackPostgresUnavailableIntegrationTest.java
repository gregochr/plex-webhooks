package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import gregochr.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

/**
 * A configured database that is down at startup. The app still starts, a Plex play still becomes a scene, and the
 * history is memory-only until the database appears; then the migration runs, and later events are stored.
 */
@DisplayName("Playback Postgres Unavailable Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaybackPostgresUnavailableIntegrationTest extends PostgresSpringTestBase {

    /** A database that does not exist yet on the embedded server: connecting fails, as if utils-pg were down. */
    private static final String LATE_DATABASE = "late_database";

    @Autowired
    private PlaybackHistoryWriter writer;

    @Autowired
    private PlaybackDatabaseLifecycle lifecycle;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(LATE_DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
    }

    @DisplayName("With the database down the app has started and a Plex play still activates the scene")
    @Test
    @Order(1)
    public void playStillBecomesASceneWithTheDatabaseDown() throws Exception {
        PlaybackEvent event = play();

        verify(hueSceneController).startMovieLightScene();
        assertEquals(PlexOutcome.Kind.SCENE_APPLIED, event.outcome());
        assertEquals("start-scene", event.sceneId());
        assertEquals(event, history.newestFirst(1).get(0));
        assertFalse(writer.isReady());
    }

    @DisplayName("Once the database exists the migration succeeds, and from then on events are stored")
    @Test
    @Order(2)
    public void historyIsStoredOnceTheDatabaseComesUp() throws Exception {
        PlaybackEvent whileDown = history.newestFirst(1).get(0);
        EmbeddedPostgresSupport.client("postgres").sql("CREATE DATABASE " + LATE_DATABASE).update();
        JdbcClient db = EmbeddedPostgresSupport.client(LATE_DATABASE);

        lifecycle.restartMigration();
        await().atMost(30, SECONDS).until(writer::isReady);
        PlaybackEvent afterwards = play();

        await().atMost(20, SECONDS).until(() -> db.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", afterwards.id()).query(Long.class).single() == 1);
        assertEquals(0L, db.sql("SELECT count(*) FROM playback_event WHERE id = :id").param("id", whileDown.id())
                .query(Long.class).single(), "events recorded while the database was down are not replayed");
    }
}
