package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import gregochr.services.config.PropertiesRepository;
import gregochr.support.EmbeddedPostgresSupport;
import gregochr.support.SettingsFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A configured database that is down at startup. The app still starts, a Plex play still becomes a scene, and the
 * history is memory-only until the database appears; then the migration runs, and later events are stored.
 */
@DisplayName("Playback Postgres Unavailable Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaybackPostgresUnavailableIntegrationTest extends PostgresSpringTestBase {

    /** A database that does not exist yet on the embedded server: connecting fails, as if utils-pg were down. */
    private static final String LATE_DATABASE = "late_database";

    private static final Path CONFIG = SettingsFiles.create(SettingsFiles.TYPICAL);

    @Autowired
    private PlaybackHistoryWriter writer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(LATE_DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
        registry.add(PropertiesRepository.CONFIG_FILE_PROPERTY, CONFIG::toString);
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

    @DisplayName("With the database down a dashboard save still succeeds and the file is written")
    @Test
    @Order(2)
    public void saveSucceedsWithTheDatabaseDown() throws Exception {
        mockMvc.perform(put("/api/config/properties").with(httpBasic("admin", "cinelight"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sse.heartbeat-interval\":\"15000\"}"))
                .andExpect(status().isOk());

        assertTrue(Files.readAllLines(CONFIG).contains("sse.heartbeat-interval=15000"));
        assertFalse(writer.isReady());
    }

    @DisplayName("Once the database exists the migration succeeds, events are stored from then on, and the save "
            + "made while it was down is caught up in the settings copy")
    @Test
    @Order(3)
    public void historyIsStoredOnceTheDatabaseComesUp() throws Exception {
        PlaybackEvent whileDown = history.newestFirst(1).get(0);
        EmbeddedPostgresSupport.client("postgres").sql("CREATE DATABASE " + LATE_DATABASE).update();
        JdbcClient db = EmbeddedPostgresSupport.client(LATE_DATABASE);

        // The migration task is already retrying every 30 s; no need to restart it.
        await().atMost(60, SECONDS).until(writer::isReady);
        PlaybackEvent afterwards = play();

        await().atMost(20, SECONDS).until(() -> db.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", afterwards.id()).query(Long.class).single() == 1);
        assertEquals(0L, db.sql("SELECT count(*) FROM playback_event WHERE id = :id").param("id", whileDown.id())
                .query(Long.class).single(), "events recorded while the database was down are not replayed");
        await().atMost(20, SECONDS).until(() -> db.sql("SELECT value FROM setting WHERE key = :key")
                .param("key", "sse.heartbeat-interval").query(String.class).optional().isPresent());
        assertEquals("15000", db.sql("SELECT value FROM setting WHERE key = 'sse.heartbeat-interval'")
                .query(String.class).single());
        assertEquals(0L, db.sql("SELECT count(*) FROM setting WHERE key = 'hue.application-key'")
                .query(Long.class).single());
    }
}
