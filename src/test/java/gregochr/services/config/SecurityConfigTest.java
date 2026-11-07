package gregochr.services.config;

import gregochr.commands.PlexCommandConfig;
import gregochr.controllers.ConfigController;
import gregochr.controllers.DebugController;
import gregochr.controllers.PlexController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.controllers.hue.HueStatusController;
import gregochr.hue.client.HueBridgeClient;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static gregochr.support.TestClocks.FIXED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain in front of the real controllers it protects. A web slice is enough: the chain,
 * ConfigController (with the real property whitelist), PlexController, DebugController and HueStatusController are
 * real, and everything behind them is mocked.
 */
@DisplayName("Security Config Tests")
@WebMvcTest(
        controllers = {ConfigController.class, PlexController.class, DebugController.class,
                HueStatusController.class},
        properties = {
                "app.admin.username=admin",
                "app.admin.password=cinelight",
                "hue.hub-address=192.168.1.100",
                "hue.status-interval=30000"})
@Import({SecurityConfig.class, ConfigService.class, ConfigurationViewService.class})
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
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseService sseService;

    @MockitoBean
    private HueSceneController hueSceneController;

    @MockitoBean
    private PlexCommandConfig plexCommandConfig;

    @MockitoBean
    private HueBridgeClient hueBridgeClient;

    @MockitoBean
    private PropertiesRepository propertiesRepository;

    @MockitoBean
    private ConfigurationViewFormatter configurationViewFormatter;

    @DisplayName("GET /api/config is accessible without authentication")
    @Test
    public void testGetConfigNoAuth() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"hueIntegrationEnabled\":true}"));
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
                .andExpect(content().json("{\"hueIntegrationEnabled\":false}"));
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
