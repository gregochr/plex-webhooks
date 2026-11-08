package gregochr.services.config;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.controllers.ConfigController;
import gregochr.controllers.ConfigSectionsController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.hue.client.HueBridgeClient;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A save through the real controller, service and repository is visible to the next read, not just on disk. */
@DisplayName("Config reads after a save")
public class ConfigFreshReadsTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.write(file, List.of("# Plex Movie", "plex.movie-play-enabled=false",
                "plex.movie-stop-enabled=true", "# Hue", "hue.start-scene=old-start"));
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "false")
                .withProperty("plex.movie-stop-enabled", "true")
                .withProperty("hue.start-scene", "old-start");
        PropertiesRepository repository = new PropertiesRepository(env, new FileSystemResource(file));
        // The live listeners behind GET /api/config, fed by the same event a real save publishes.
        HueSceneController hueSceneController = new HueSceneController(mock(HueBridgeClient.class), "old-start", "");
        PlexCommandConfig plexCommandConfig = new PlexCommandConfig(Map.of(PlexMediaType.MOVIE,
                new PlexMediaTypeConfig(false, false, true, false, false)));
        ApplicationEventPublisher publisher = event -> {
            hueSceneController.onPropertiesUpdated((PropertiesUpdatedEvent) event);
            plexCommandConfig.onPropertiesUpdated((PropertiesUpdatedEvent) event);
        };
        ConfigurationViewService viewService = new ConfigurationViewService(repository,
                new ConfigurationViewFormatter(), publisher);
        ConfigService configService = new ConfigService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ConfigController(configService, mock(SseService.class), viewService,
                        new ClientConfigService(configService, hueSceneController, plexCommandConfig)),
                new ConfigSectionsController(new ConfigSectionsService(viewService, false))).build();
    }

    private static final String PLAY = "$.sections[*].items[?(@.key=='plex.movie-play-enabled')].value";
    private static final String STOP = "$.sections[*].items[?(@.key=='plex.movie-stop-enabled')].value";

    @DisplayName("PUT plex-commands then GET sections returns the new flag values")
    @Test
    public void testPlexCommandsFreshAfterPut() throws Exception {
        mockMvc.perform(get("/api/config/sections"))
                .andExpect(jsonPath(PLAY).value("false"));

        mockMvc.perform(put("/api/config/plex-commands/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plex.movie-play-enabled\":true,\"plex.movie-stop-enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/config/sections"))
                .andExpect(jsonPath(PLAY).value("true"))
                .andExpect(jsonPath(STOP).value("false"));
    }

    @DisplayName("PUT plex-commands then GET config returns the new live flags")
    @Test
    public void testPlexCommandsFreshInLiveConfig() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-play-enabled']").value(false));

        mockMvc.perform(put("/api/config/plex-commands/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plex.movie-play-enabled\":true,\"plex.movie-stop-enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-play-enabled']").value(true))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-stop-enabled']").value(false));
    }

    @DisplayName("PUT hue-scene then GET config returns the new scene")
    @Test
    public void testHueScenesFreshAfterPut() throws Exception {
        mockMvc.perform(put("/api/config/hue-scene").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"hue.start-scene\",\"sceneId\":\"new-start\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.scenes.start").value("new-start"));
    }

    @DisplayName("PUT plex-commands is visible in GET sections straight away")
    @Test
    public void testSectionsFreshAfterPut() throws Exception {
        mockMvc.perform(put("/api/config/plex-commands/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plex.movie-play-enabled\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/config/sections"))
                .andExpect(jsonPath("$.sections[0].items[0].key").value("plex.movie-play-enabled"))
                .andExpect(jsonPath("$.sections[0].items[0].value").value("true"));
    }
}
