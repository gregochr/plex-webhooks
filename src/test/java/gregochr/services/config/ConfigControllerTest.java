package gregochr.services.config;

import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Config Controller Tests")
public class ConfigControllerTest {

    private ConfigService configService;
    private SseService sseService;
    private ConfigurationViewService configurationViewService;
    private ConfigController controller;

    @BeforeEach
    public void setUp() {
        configService = mock(ConfigService.class);
        sseService = mock(SseService.class);
        configurationViewService = mock(ConfigurationViewService.class);
        controller = new ConfigController(configService, sseService, configurationViewService);
    }

    @DisplayName("Get config returns hue integration status")
    @Test
    public void testGetConfigReturnsHueStatus() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> result = controller.getConfig();

        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
        verify(configService).isHueIntegrationEnabled();
    }

    @DisplayName("Set hue enabled to true updates service")
    @Test
    public void testSetHueEnabledTrue() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", true));

        verify(configService).setHueIntegrationEnabled(true);
        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled to false updates service")
    @Test
    public void testSetHueEnabledFalse() {
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", false));

        verify(configService).setHueIntegrationEnabled(false);
        assertFalse((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled defaults to true when key missing")
    @Test
    public void testSetHueEnabledDefaultsToTrue() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.setHueEnabled(Map.of());

        verify(configService).setHueIntegrationEnabled(true);
    }

    @DisplayName("Set hue enabled broadcasts config via SSE")
    @Test
    public void testSetHueEnabledBroadcastsConfig() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.setHueEnabled(Map.of("enabled", true));

        verify(sseService).broadcastConfig(Map.of("hueIntegrationEnabled", true));
    }

    @DisplayName("GET plex-commands returns movie config values")
    @Test
    public void testGetPlexCommandsMovie() {
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("plex.movie-play-enabled", true);
        expected.put("plex.movie-stop-enabled", false);
        when(configurationViewService.getProperties("movie")).thenReturn(expected);

        ResponseEntity<Map<String, Boolean>> response = controller.getPlexCommands("movie");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
    }

    @DisplayName("GET plex-commands returns 404 for unknown media type")
    @Test
    public void testGetPlexCommandsUnknownType() {
        ResponseEntity<Map<String, Boolean>> response = controller.getPlexCommands("unknown");

        assertEquals(404, response.getStatusCode().value());
    }

    @DisplayName("PUT plex-commands saves to disk and returns values")
    @Test
    public void testSavePlexCommands() throws IOException {
        Path fakePath = Path.of("/tmp/test.properties");
        when(configurationViewService.resolvePropertiesPath()).thenReturn(fakePath);
        doNothing().when(configurationViewService).saveProperties(any(), eq(fakePath));

        Map<String, Boolean> body = new LinkedHashMap<>();
        body.put("plex.movie-play-enabled", true);
        body.put("plex.movie-stop-enabled", false);

        ResponseEntity<Map<String, Boolean>> response = controller.savePlexCommands("movie", body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(body, response.getBody());
        verify(configurationViewService).saveProperties(any(), eq(fakePath));
    }

    @DisplayName("PUT plex-commands returns 404 for unknown media type")
    @Test
    public void testSavePlexCommandsUnknownType() {
        Map<String, Boolean> body = Map.of("plex.unknown-play-enabled", true);

        ResponseEntity<Map<String, Boolean>> response = controller.savePlexCommands("unknown", body);

        assertEquals(404, response.getStatusCode().value());
    }

    @DisplayName("GET hue-scenes returns current scene config")
    @Test
    public void testGetHueScenes() {
        Map<String, String> scenes = Map.of(
                "hue.start-scene", "abc-123",
                "hue.stop-scene", "xyz-456"
        );
        when(configurationViewService.getSceneProperties()).thenReturn(scenes);

        Map<String, String> result = controller.getHueScenes();

        assertEquals("abc-123", result.get("hue.start-scene"));
        assertEquals("xyz-456", result.get("hue.stop-scene"));
    }

    @DisplayName("PUT hue-scene saves start scene to disk")
    @Test
    public void testSaveHueSceneStart() throws IOException {
        Path fakePath = Path.of("/tmp/test.properties");
        when(configurationViewService.resolvePropertiesPath()).thenReturn(fakePath);
        doNothing().when(configurationViewService).saveProperties(any(), eq(fakePath));

        Map<String, String> body = Map.of("key", "hue.start-scene", "sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hue.start-scene", response.getBody().get("key"));
        assertEquals("abc-123", response.getBody().get("sceneId"));
    }

    @DisplayName("PUT hue-scene saves stop scene to disk")
    @Test
    public void testSaveHueSceneStop() throws IOException {
        Path fakePath = Path.of("/tmp/test.properties");
        when(configurationViewService.resolvePropertiesPath()).thenReturn(fakePath);
        doNothing().when(configurationViewService).saveProperties(any(), eq(fakePath));

        Map<String, String> body = Map.of("key", "hue.stop-scene", "sceneId", "xyz-456");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hue.stop-scene", response.getBody().get("key"));
    }

    @DisplayName("PUT hue-scene returns 400 for invalid key")
    @Test
    public void testSaveHueSceneInvalidKey() {
        Map<String, String> body = Map.of("key", "hue.invalid-key", "sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(400, response.getStatusCode().value());
    }

    @DisplayName("PUT hue-scene returns 400 when key is missing")
    @Test
    public void testSaveHueSceneMissingKey() {
        Map<String, String> body = Map.of("sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(400, response.getStatusCode().value());
    }
}
