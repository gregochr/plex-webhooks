package gregochr.services.config;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.controllers.PlaybackController;
import gregochr.controllers.hue.HueSceneCatalog;
import gregochr.controllers.hue.HueSceneCatalogController;
import gregochr.services.events.PlaybackHistory;
import gregochr.services.events.PlaybackService;
import gregochr.services.events.PlexPayloadSanitizer;
import gregochr.controllers.ConfigController;
import gregochr.controllers.ConfigSectionsController;
import gregochr.controllers.DebugController;
import gregochr.controllers.PlexController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.controllers.hue.HueStatusController;
import gregochr.hue.client.HueBridgeClient;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain in front of the real controllers it protects. A web slice is enough: the chain,
 * ConfigController (with the real property whitelist), PlexController, DebugController and HueStatusController are
 * real, and everything behind them is mocked.
 */
@DisplayName("Security Config Tests")
@WebMvcTest(
        controllers = {ConfigController.class, PlexController.class, DebugController.class,
                HueStatusController.class, PlaybackController.class, HueSceneCatalogController.class,
                ConfigSectionsController.class},
        properties = {
                "app.admin.username=admin",
                "app.admin.password=cinelight",
                "hue.hub-address=192.168.1.100",
                "hue.status-interval=30000",
                "hue.application-key=SECRET-CANARY-1234"})
@Import({SecurityConfig.class, ConfigService.class, ConfigurationViewService.class, ClientConfigService.class,
        ConfigSectionsService.class,
        PlaybackService.class, PlaybackHistory.class, PlexPayloadSanitizer.class})
public class SecurityConfigTest {

    @TestConfiguration
    static class FixedTimeConfig {
        @Bean
        Clock clock() {
            return FIXED;
        }

        @Bean
        TaskScheduler taskScheduler() {
            return mock(TaskScheduler.class);
        }

        @Bean
        PlexCommandConfig plexCommandConfig() {
            Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
            configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, false, false, false, false));
            return new PlexCommandConfig(configs);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseService sseService;

    @MockitoBean
    private HueSceneController hueSceneController;

    @MockitoBean
    private HueSceneCatalog hueSceneCatalog;

    @MockitoBean
    private HueBridgeClient hueBridgeClient;

    @MockitoBean
    private PropertiesRepository propertiesRepository;

    @MockitoBean
    private ConfigurationViewFormatter configurationViewFormatter;

    @DisplayName("GET /api/config is accessible without authentication and returns the shared config shape")
    @Test
    public void testGetConfigNoAuth() throws Exception {
        when(hueSceneController.getStartScene()).thenReturn("start-id");
        when(hueSceneController.getStopScene()).thenReturn("stop-id");

        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hueIntegrationEnabled").value(true))
                .andExpect(jsonPath("$.scenes.start").value("start-id"))
                .andExpect(jsonPath("$.scenes.stop").value("stop-id"))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-play-enabled']").value(true))
                .andExpect(jsonPath("$.plexCommands.movie['plex.movie-resume-enabled']").value(false))
                .andExpect(jsonPath("$.plexCommands.photo['plex.photo-play-enabled']").value(false));
    }

    @DisplayName("GET /api/config never carries a credential value, to anyone")
    @Test
    public void testGetConfigHasNoCredentials() throws Exception {
        String body = mockMvc.perform(get("/api/config")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertFalse(body.contains("SECRET-CANARY-1234"), body);
        assertFalse(body.contains("cinelight"), body);
        assertFalse(body.toLowerCase().contains("password"), body);
        assertFalse(body.toLowerCase().contains("application-key"), body);
    }

    @DisplayName("GET /api/config/sections is public and carries no credential value")
    @Test
    public void testGetSectionsNoAuthAndNoCredentials() throws Exception {
        when(propertiesRepository.readAllEntries()).thenReturn(List.of());
        when(configurationViewFormatter.buildSections(List.of())).thenReturn(List.of(
                new ConfigurationViewSection("Hue", List.of(
                        new ConfigurationViewItem("hue.application-key", "Application Key", "SECRET-CANARY-1234",
                                "Set", "secret", false, true),
                        new ConfigurationViewItem("hue.status-interval", "Status Interval", "30000", "30,000",
                                "number", true, false)), false)));

        String body = mockMvc.perform(get("/api/config/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminTabEnabled").value(true))
                .andExpect(jsonPath("$.sections[0].items[0].set").value(true))
                .andExpect(jsonPath("$.sections[0].items[0].value").doesNotExist())
                .andExpect(jsonPath("$.sections[0].items[1].writable").value(true))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("SECRET-CANARY-1234"), body);
    }

    @DisplayName("GET /api/playback/history and /api/playback/now are public")
    @Test
    public void testPlaybackEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/api/playback/history")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/playback/now")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").isString());
    }

    @DisplayName("GET /api/hue/scenes is public")
    @Test
    public void testHueScenesArePublic() throws Exception {
        when(hueSceneCatalog.view()).thenReturn(new HueSceneCatalog.View(List.of(),
                new HueSceneCatalog.Selection("a", null), new HueSceneCatalog.Selection("b", null)));

        mockMvc.perform(get("/api/hue/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start.id").value("a"));
    }

    @DisplayName("PUT /api/config/** returns 401 without credentials, whichever sub-path")
    @Test
    public void testEveryConfigWriteRequiresAuth() throws Exception {
        for (String path : List.of("/api/config/hue-scene", "/api/config/plex-commands/movie",
                "/api/config/properties", "/api/config/hue-enabled", "/api/config/anything-else")) {
            mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @DisplayName("The retired GET plex-commands and GET hue-scenes config reads reach no handler")
    @Test
    public void testRetiredConfigReadsAreGone() throws Exception {
        // A PUT handler still lives at the plex-commands path, so a GET there is a 405; hue-scenes has none, so 404.
        String plex = mockMvc.perform(get("/api/config/plex-commands/movie"))
                .andExpect(status().isMethodNotAllowed()).andReturn().getResponse().getContentAsString();
        String scenes = mockMvc.perform(get("/api/config/hue-scenes"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertFalse(plex.contains("plex.movie-"), plex);
        assertFalse(scenes.contains("hue.start-scene"), scenes);
    }

    @DisplayName("POST /api/debug/** returns 401 without credentials, whichever command")
    @Test
    public void testEveryDebugCommandRequiresAuth() throws Exception {
        for (String command : List.of("play", "pause", "resume", "stop", "scrobble", "anything")) {
            mockMvc.perform(post("/api/debug/" + command)).andExpect(status().isUnauthorized());
        }
    }

    @DisplayName("A debug event with credentials goes through the pipeline and reports its outcome")
    @Test
    public void testDebugReportsOutcome() throws Exception {
        mockMvc.perform(post("/api/debug/play")
                        .with(httpBasic("admin", "cinelight"))
                        .param("type", "episode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.outcome").value("IGNORED_RULE_OFF"));
    }

    @DisplayName("An unauthenticated write sent as an XHR gets a 401 with no WWW-Authenticate challenge")
    @Test
    public void testXhrWriteGetsBare401() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @DisplayName("Every protected write sent as an XHR, with no or wrong credentials, gets a bare 401")
    @Test
    public void testEveryXhrWriteGetsBare401() throws Exception {
        for (String path : List.of("/api/config/hue-scene", "/api/config/plex-commands/movie",
                "/api/config/properties")) {
            mockMvc.perform(put(path).header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
            mockMvc.perform(put(path).header("X-Requested-With", "XMLHttpRequest")
                            .with(httpBasic("admin", "wrong"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
        }
        mockMvc.perform(post("/api/debug/play").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @DisplayName("Without the XHR header a 401 still carries the Basic challenge (curl and scripts keep working)")
    @Test
    public void testNonXhrWriteKeepsChallenge() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @DisplayName("PUT /api/config/hue-enabled returns 401 without authentication")
    @Test
    public void testPutConfigNoAuth() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("PUT /api/config/hue-enabled returns 200 with valid credentials")
    @Test
    public void testPutConfigWithAuth() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hueIntegrationEnabled").value(false));
    }

    @DisplayName("PUT /api/config/hue-enabled returns 401 with wrong credentials")
    @Test
    public void testPutConfigWrongCredentials() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .with(httpBasic("admin", "wrongpassword"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("PUT /api/config/hue-enabled returns 401 for the right password under another username")
    @Test
    public void testPutConfigWrongUsername() throws Exception {
        mockMvc.perform(put("/api/config/hue-enabled")
                        .with(httpBasic("root", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("POST /plex is accessible without authentication")
    @Test
    public void testPlexWebhookNoAuth() throws Exception {
        String payload = "{\"event\":\"media.play\","
                + "\"Metadata\":{\"type\":\"movie\",\"title\":\"Test\",\"duration\":1000}}";
        mockMvc.perform(multipart("/plex")
                        .param("payload", payload))
                .andExpect(status().isOk());
    }

    @DisplayName("POST /api/debug/play returns 401 without authentication")
    @Test
    public void testDebugNoAuth() throws Exception {
        mockMvc.perform(post("/api/debug/play")
                        .param("type", "movie"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("POST /api/debug/play returns 200 with valid credentials")
    @Test
    public void testDebugWithAuth() throws Exception {
        mockMvc.perform(post("/api/debug/play")
                        .with(httpBasic("admin", "cinelight"))
                        .param("type", "movie"))
                .andExpect(status().isOk());
    }

    @DisplayName("PUT /api/config/properties accepts a whitelisted interval with valid credentials")
    @Test
    public void testPutPropertiesAcceptsWhitelistedKey() throws Exception {
        mockMvc.perform(put("/api/config/properties")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sse.heartbeat-interval\":\"20000\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"sse.heartbeat-interval\":\"20000\"}"));
    }

    @DisplayName("PUT /api/config/properties returns 401 without authentication")
    @Test
    public void testPutPropertiesNoAuth() throws Exception {
        mockMvc.perform(put("/api/config/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sse.heartbeat-interval\":\"20000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("PUT /api/config/properties rejects hue.application-key even with valid credentials")
    @Test
    public void testPutPropertiesRejectsApplicationKey() throws Exception {
        mockMvc.perform(put("/api/config/properties")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hue.application-key\":\"overwritten\"}"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("PUT /api/config/properties rejects app.admin.password even with valid credentials")
    @Test
    public void testPutPropertiesRejectsAdminPassword() throws Exception {
        mockMvc.perform(put("/api/config/properties")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app.admin.password\":\"overwritten\"}"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("GET /api/hue/status is accessible without authentication")
    @Test
    public void testGetHueStatusNoAuth() throws Exception {
        when(hueBridgeClient.getHubId()).thenReturn("001788fffe7107c8");

        mockMvc.perform(get("/api/hue/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"hubId\":\"001788fffe7107c8\",\"timestamp\":\"2024-03-15T12:00:00Z\"}"));
    }
}
