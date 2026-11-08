package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import gregochr.services.config.PropertiesRepository;
import gregochr.support.SettingsFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A database URL nothing listens on: the connection is refused at once, and the app does not care. */
@DisplayName("Playback Postgres Unreachable Integration Tests")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/cinelight",
        "spring.datasource.username=cinelight",
        "spring.datasource.password=not-a-real-password",
        "management.endpoint.health.group.database.include=db"})
public class PlaybackPostgresUnreachableIntegrationTest extends PostgresSpringTestBase {

    private static final Path CONFIG = SettingsFiles.create(SettingsFiles.TYPICAL);

    @DynamicPropertySource
    static void settingsFile(DynamicPropertyRegistry registry) {
        registry.add(PropertiesRepository.CONFIG_FILE_PROPERTY, CONFIG::toString);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlaybackHistoryWriter writer;

    @DisplayName("A dead database turns the database health group DOWN but never the container group Docker reads")
    @Test
    public void deadDatabaseNeverMakesTheContainerUnhealthy() throws Exception {
        mockMvc.perform(get("/actuator/health/database")).andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}"));
        mockMvc.perform(get("/actuator/health/container")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @DisplayName("The context starts with the pool in place but never connected, and a Plex play still becomes a "
            + "scene")
    @Test
    public void playBecomesASceneWithAnUnreachableDatabase() throws Exception {
        PlaybackEvent event = play();

        assertNotNull(dataSource);
        verify(hueSceneController).startMovieLightScene();
        assertEquals(PlexOutcome.Kind.SCENE_APPLIED, event.outcome());
        assertEquals(event, history.newestFirst(1).get(0));
        assertFalse(writer.isReady());
    }

    @DisplayName("With an unreachable database a dashboard save succeeds and the file is written")
    @Test
    public void saveSucceedsWithAnUnreachableDatabase() throws Exception {
        mockMvc.perform(put("/api/config/properties").with(httpBasic("admin", "cinelight"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sse.heartbeat-interval\":\"15000\"}"))
                .andExpect(status().isOk());

        assertTrue(Files.readAllLines(CONFIG).contains("sse.heartbeat-interval=15000"));
        assertFalse(writer.isReady());
    }
}
