package gregochr.services.config;

import gregochr.controllers.ConfigController;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(configurationViewService.isValidMediaType("movie")).thenReturn(true);
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("plex.movie-play-enabled", true);
        expected.put("plex.movie-stop-enabled", false);
        when(configurationViewService.getPlexProperties("movie")).thenReturn(expected);

        ResponseEntity<Map<String, Boolean>> response = controller.getPlexCommands("movie");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
    }

    @DisplayName("GET plex-commands returns 404 for unknown media type")
    @Test
    public void testGetPlexCommandsUnknownType() {
        when(configurationViewService.isValidMediaType("unknown")).thenReturn(false);

        ResponseEntity<Map<String, Boolean>> response = controller.getPlexCommands("unknown");

        assertEquals(404, response.getStatusCode().value());
    }

    @DisplayName("PUT plex-commands saves to disk and returns values")
    @Test
    public void testSavePlexCommands() throws Exception {
        when(configurationViewService.isValidMediaType("movie")).thenReturn(true);

        Map<String, Boolean> body = new LinkedHashMap<>();
        body.put("plex.movie-play-enabled", true);
        body.put("plex.movie-stop-enabled", false);

        ResponseEntity<Map<String, Boolean>> response = controller.savePlexCommands("movie", body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(body, response.getBody());
        verify(configurationViewService).saveProperties(
                Map.of("plex.movie-play-enabled", "true", "plex.movie-stop-enabled", "false"));
    }

    @DisplayName("PUT plex-commands saves only keys that start with the media type's prefix")
    @Test
    public void testSavePlexCommandsFiltersByPrefix() throws Exception {
        when(configurationViewService.isValidMediaType("movie")).thenReturn(true);
        Map<String, Boolean> body = new LinkedHashMap<>();
        body.put("plex.movie-play-enabled", true);
        body.put("plex.episode-play-enabled", true);
        body.put("plex.movie", true);
        body.put("hue.application-key", true);

        controller.savePlexCommands("movie", body);

        verify(configurationViewService).saveProperties(Map.of("plex.movie-play-enabled", "true"));
    }

    @DisplayName("PUT plex-commands returns 404 for unknown media type")
    @Test
    public void testSavePlexCommandsUnknownType() throws Exception {
        when(configurationViewService.isValidMediaType("unknown")).thenReturn(false);
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
    public void testSaveHueSceneStart() throws Exception {
        when(configurationViewService.isValidSceneKey("hue.start-scene")).thenReturn(true);

        Map<String, String> body = Map.of("key", "hue.start-scene", "sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hue.start-scene", response.getBody().get("key"));
        assertEquals("abc-123", response.getBody().get("sceneId"));
        verify(configurationViewService).saveProperties(Map.of("hue.start-scene", "abc-123"));
    }

    @DisplayName("PUT hue-scene saves stop scene to disk")
    @Test
    public void testSaveHueSceneStop() throws Exception {
        when(configurationViewService.isValidSceneKey("hue.stop-scene")).thenReturn(true);

        Map<String, String> body = Map.of("key", "hue.stop-scene", "sceneId", "xyz-456");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hue.stop-scene", response.getBody().get("key"));
        verify(configurationViewService).saveProperties(Map.of("hue.stop-scene", "xyz-456"));
    }

    @DisplayName("PUT hue-scene returns 400 for invalid key")
    @Test
    public void testSaveHueSceneInvalidKey() throws Exception {
        when(configurationViewService.isValidSceneKey("hue.invalid-key")).thenReturn(false);
        Map<String, String> body = Map.of("key", "hue.invalid-key", "sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(400, response.getStatusCode().value());
    }

    @DisplayName("PUT hue-scene returns 400 when key is missing")
    @Test
    public void testSaveHueSceneMissingKey() throws Exception {
        Map<String, String> body = Map.of("sceneId", "abc-123");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(400, response.getStatusCode().value());
    }

    @DisplayName("PUT properties saves valid key to disk")
    @Test
    public void testSaveProperties() throws Exception {
        when(configurationViewService.getValidPropertyKeys()).thenReturn(Set.of("sse.heartbeat-interval"));

        Map<String, String> body = Map.of("sse.heartbeat-interval", "5000");

        ResponseEntity<Map<String, String>> response = controller.saveProperties(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("5000", response.getBody().get("sse.heartbeat-interval"));
        verify(configurationViewService).saveProperties(Map.of("sse.heartbeat-interval", "5000"));
    }

    @DisplayName("PUT properties returns 400 for invalid key")
    @Test
    public void testSavePropertiesInvalidKey() throws Exception {
        when(configurationViewService.getValidPropertyKeys()).thenReturn(Set.of("sse.heartbeat-interval"));
        Map<String, String> body = Map.of("hue.application-key", "stolen");

        ResponseEntity<Map<String, String>> response = controller.saveProperties(body);

        assertEquals(400, response.getStatusCode().value());
    }

    @DisplayName("PUT properties ignores invalid keys and saves valid ones")
    @Test
    public void testSavePropertiesFiltersKeys() throws Exception {
        when(configurationViewService.getValidPropertyKeys()).thenReturn(Set.of("sse.heartbeat-interval"));

        Map<String, String> body = new LinkedHashMap<>();
        body.put("sse.heartbeat-interval", "8000");
        body.put("hue.application-key", "stolen");

        ResponseEntity<Map<String, String>> response = controller.saveProperties(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("8000", response.getBody().get("sse.heartbeat-interval"));
        verify(configurationViewService).saveProperties(Map.of("sse.heartbeat-interval", "8000"));
    }

    @DisplayName("PUT properties with no keys returns 400 and saves nothing")
    @Test
    public void testSavePropertiesEmptyBody() throws Exception {
        when(configurationViewService.getValidPropertyKeys()).thenReturn(Set.of("sse.heartbeat-interval"));

        ResponseEntity<Map<String, String>> response = controller.saveProperties(Map.of());

        assertEquals(400, response.getStatusCode().value());
        verify(configurationViewService, never()).saveProperties(Map.of());
    }

    @DisplayName("PUT hue-scene returns 400 when the scene id is missing")
    @Test
    public void testSaveHueSceneMissingSceneId() throws Exception {
        Map<String, String> body = Map.of("key", "hue.start-scene");

        ResponseEntity<Map<String, String>> response = controller.saveHueScene(body);

        assertEquals(400, response.getStatusCode().value());
        verify(configurationViewService, never()).saveProperties(Map.of());
    }
}
