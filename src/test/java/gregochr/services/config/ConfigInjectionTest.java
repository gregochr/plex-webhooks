package gregochr.services.config;

import gregochr.controllers.ConfigController;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Settings writes cannot be used to add lines (such as a new admin password) to the settings file. */
@DisplayName("Config write injection")
public class ConfigInjectionTest {

    private static final String UUID_SCENE = "04a6c3e1-0000-4abc-9def-0123456789ab";

    private MockMvc mockMvc;
    private Path file;
    private String original;

    @BeforeEach
    public void setUp(@TempDir Path dir) throws IOException {
        file = dir.resolve("application.properties");
        Files.write(file, List.of("# Plex Movie", "plex.movie-play-enabled=false", "# Hue",
                "hue.start-scene=old-start"));
        original = Files.readString(file);
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "false")
                .withProperty("hue.start-scene", "old-start");
        PropertiesRepository repository = new PropertiesRepository(env, new FileSystemResource(file));
        ConfigurationViewService viewService = new ConfigurationViewService(repository,
                new ConfigurationViewFormatter(), mock(ApplicationEventPublisher.class));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ConfigController(mock(ConfigService.class), mock(SseService.class), viewService,
                        mock(ClientConfigService.class))).build();
    }

    @DisplayName("a plex-commands key with an embedded newline is ignored: no new line reaches the file")
    @Test
    public void testPlexKeyInjectionIgnored() throws Exception {
        mockMvc.perform(put("/api/config/plex-commands/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plex.movie-x\\napp.admin.password\":true}"))
                .andExpect(status().isOk());

        assertEquals(original, Files.readString(file));
        assertFalse(Files.readString(file).contains("app.admin.password"));
        mockMvc.perform(get("/api/config/plex-commands/movie"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$['plex.movie-play-enabled']").value(false));
    }

    @DisplayName("unknown plex keys are ignored but known ones in the same request are saved")
    @Test
    public void testKnownKeySavedAlongsideUnknown() throws Exception {
        mockMvc.perform(put("/api/config/plex-commands/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plex.movie-play-enabled\":true,\"plex.movie-bogus-enabled\":true}"))
                .andExpect(status().isOk());

        assertEquals(original.replace("plex.movie-play-enabled=false", "plex.movie-play-enabled=true"),
                Files.readString(file));
    }

    @DisplayName("a scene id with a newline is rejected with 400 and the file is unchanged")
    @Test
    public void testSceneIdInjectionRejected() throws Exception {
        mockMvc.perform(put("/api/config/hue-scene").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"hue.start-scene\",\"sceneId\":\"abc\\napp.admin.password=x\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(original, Files.readString(file));
        mockMvc.perform(get("/api/config/hue-scenes"))
                .andExpect(jsonPath("$['hue.start-scene']").value("old-start"));
    }

    @DisplayName("a valid UUID scene id still saves")
    @Test
    public void testValidSceneIdSaves() throws Exception {
        mockMvc.perform(put("/api/config/hue-scene").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"hue.start-scene\",\"sceneId\":\"" + UUID_SCENE + "\"}"))
                .andExpect(status().isOk());

        assertEquals(original.replace("old-start", UUID_SCENE), Files.readString(file));
    }
}
