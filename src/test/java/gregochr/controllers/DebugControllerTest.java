package gregochr.controllers;

import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@DisplayName("Debug Controller Tests")
public class DebugControllerTest {

    private HueSceneController hueSceneController;
    private LocationController locationController;
    private SunriseSunsetController sunriseSunsetController;
    private ConfigService configService;
    private DebugController debugController;

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        locationController = mock(LocationController.class);
        sunriseSunsetController = mock(SunriseSunsetController.class);
        configService = mock(ConfigService.class);
        debugController = new DebugController(hueSceneController, locationController, sunriseSunsetController, configService);
    }

    @DisplayName("Play command triggers startMovieLightScene")
    @Test
    public void testPlayCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("play", "movie");

        assertEquals("play", result.get("command"));
        assertEquals("movie", result.get("type"));
        assertTrue((Boolean) result.get("success"));
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("Stop command triggers stopMovieLightScene")
    @Test
    public void testStopCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("stop", "movie");

        assertEquals("stop", result.get("command"));
        assertTrue((Boolean) result.get("success"));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Pause command triggers stopMovieLightScene")
    @Test
    public void testPauseCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("pause", "movie");

        assertEquals("pause", result.get("command"));
        assertTrue((Boolean) result.get("success"));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Resume command triggers startMovieLightScene")
    @Test
    public void testResumeCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("resume", "movie");

        assertEquals("resume", result.get("command"));
        assertTrue((Boolean) result.get("success"));
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("Scrobble command triggers stopMovieLightScene")
    @Test
    public void testScrobbleCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("scrobble", "movie");

        assertEquals("scrobble", result.get("command"));
        assertTrue((Boolean) result.get("success"));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Unknown command returns false and does not call Hue")
    @Test
    public void testUnknownCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("unknown", "movie");

        assertEquals("unknown", result.get("command"));
        assertFalse((Boolean) result.get("success"));
        verify(hueSceneController, never()).startMovieLightScene();
        verify(hueSceneController, never()).stopMovieLightScene();
    }

    @DisplayName("Command with episode type passes type correctly")
    @Test
    public void testEpisodeType() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);

        Map<String, Object> result = debugController.triggerCommand("play", "episode");

        assertEquals("play", result.get("command"));
        assertEquals("episode", result.get("type"));
        assertFalse((Boolean) result.get("success"));
    }

    @DisplayName("Command returns false when Hue integration disabled")
    @Test
    public void testHueDisabled() {
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        Map<String, Object> result = debugController.triggerCommand("play", "movie");

        assertEquals("play", result.get("command"));
        assertFalse((Boolean) result.get("success"));
        verify(hueSceneController, never()).startMovieLightScene();
    }
}
