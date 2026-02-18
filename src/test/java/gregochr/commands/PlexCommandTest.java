package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class PlexCommandTest {

    private HueSceneController hueSceneController;
    private ConfigService configService;
    private PlexCommandConfig plexCommandConfig;

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        configService = mock(ConfigService.class);
        plexCommandConfig = mock(PlexCommandConfig.class);
    }

    @DisplayName("Test Plex Play Movie Command")
    @Test
    public void testPlexPlayMovieCommand() {
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(hueSceneController.startMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexPlayCommand.execute(hueSceneController, configService, plexCommandConfig));
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("Test Plex Play TV Show Command")
    @Test
    public void testPlexPlayTVShowCommand() {
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexPlayCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Resume Movie Command")
    @Test
    public void testPlexResumeMovieCommand() {
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(hueSceneController.startMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexResumeCommand.execute(hueSceneController, configService, plexCommandConfig));
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("Test Plex Resume TV Show Command")
    @Test
    public void testPlexResumeTVShowCommand() {
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexResumeCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Stop Movie Command")
    @Test
    public void testPlexStopMovieCommand() {
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexStopCommand.execute(hueSceneController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Test Plex Stop TV Show Command")
    @Test
    public void testPlexStopTVShowCommand() {
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexStopCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Pause Movie Command")
    @Test
    public void testPlexPauseMovieCommand() {
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexPauseCommand.execute(hueSceneController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Test Plex Pause TV Show Command")
    @Test
    public void testPlexPauseTVShowCommand() {
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexPauseCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Scrobble Movie Command")
    @Test
    public void testPlexScrobbleMovieCommand() {
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexScrobbleCommand.execute(hueSceneController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Test Plex Scrobble TV Show Command")
    @Test
    public void testPlexScrobbleTVShowCommand() {
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexScrobbleCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Play Movie Command returns false when command disabled by config")
    @Test
    public void testPlexPlayMovieCommandDisabledByConfig() {
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE)).thenReturn(false);

        assertFalse(plexPlayCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Play Movie Command skips Hue when disabled")
    @Test
    public void testPlexPlayMovieCommandSkipsHueWhenDisabled() {
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexPlayCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Stop Movie Command skips Hue when disabled")
    @Test
    public void testPlexStopMovieCommandSkipsHueWhenDisabled() {
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexStopCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Pause Movie Command skips Hue when disabled")
    @Test
    public void testPlexPauseMovieCommandSkipsHueWhenDisabled() {
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexPauseCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Resume Movie Command skips Hue when disabled")
    @Test
    public void testPlexResumeMovieCommandSkipsHueWhenDisabled() {
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexResumeCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Scrobble Movie Command skips Hue when disabled")
    @Test
    public void testPlexScrobbleMovieCommandSkipsHueWhenDisabled() {
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexScrobbleCommand.execute(hueSceneController, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }
}
