package gregochr.controllers;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.clients.LocationClient;
import gregochr.clients.SunriseSunsetClient;
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
    private LocationClient locationClient;
    private SunriseSunsetClient sunriseSunsetClient;
    private ConfigService configService;
    private PlexCommandConfig plexCommandConfig;
    private DebugController debugController;

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        locationClient = mock(LocationClient.class);
        sunriseSunsetClient = mock(SunriseSunsetClient.class);
        configService = mock(ConfigService.class);
        plexCommandConfig = mock(PlexCommandConfig.class);
        debugController = new DebugController(hueSceneController, locationClient, sunriseSunsetClient, configService, plexCommandConfig);
    }

    @DisplayName("Play command triggers startMovieLightScene")
    @Test
    public void testPlayCommand() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE)).thenReturn(true);
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
        when(plexCommandConfig.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE)).thenReturn(true);
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
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE)).thenReturn(true);
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
        when(plexCommandConfig.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE)).thenReturn(true);
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
        when(plexCommandConfig.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE)).thenReturn(true);
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
