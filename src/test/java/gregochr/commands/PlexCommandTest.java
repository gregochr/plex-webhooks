package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.dto.LocationResultDto;
import gregochr.controllers.dto.LocationResponseDto;
import gregochr.controllers.dto.SunriseSunsetResultDto;
import gregochr.controllers.dto.SunriseSunsetResponseDto;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class PlexCommandTest {

    @DisplayName("Test Plex Play Movie Command")
    @Test
    public void testPlexPlayMovieCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        LocationResponseDto locationResponse = new LocationResponseDto();
        LocationResultDto locationResult = new LocationResultDto();
        locationResult.setLatitude(1);
        locationResult.setLongitude(1);
        locationResponse.setStatus(200);
        locationResponse.setResult(locationResult);

        SunriseSunsetResponseDto sunriseSunsetResponse = new SunriseSunsetResponseDto();
        SunriseSunsetResultDto sunriseSunsetResult = new SunriseSunsetResultDto();
        sunriseSunsetResponse.setStatus("200");
        sunriseSunsetResponse.setTzId("utc");
        sunriseSunsetResult.setSunset("18:00:00");
        sunriseSunsetResponse.setResult(sunriseSunsetResult);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationResponse);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetResponse);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Play TV Show Command")
    @Test
    public void testPlexPlayTVShowCommand() {
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexPlayCommand.execute(null, null, null, configService, plexCommandConfig));
    }
    @DisplayName("Test Plex Resume Movie Command")
    @Test
    public void testPlexResumeMovieCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        LocationResponseDto locationResponse = new LocationResponseDto();
        LocationResultDto locationResult = new LocationResultDto();
        locationResult.setLatitude(1);
        locationResult.setLongitude(1);
        locationResponse.setStatus(200);
        locationResponse.setResult(locationResult);

        SunriseSunsetResponseDto sunriseSunsetResponse = new SunriseSunsetResponseDto();
        SunriseSunsetResultDto sunriseSunsetResult = new SunriseSunsetResultDto();
        sunriseSunsetResponse.setStatus("200");
        sunriseSunsetResponse.setTzId("utc");
        sunriseSunsetResult.setSunset("18:00:00");
        sunriseSunsetResponse.setResult(sunriseSunsetResult);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationResponse);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetResponse);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexResumeCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Resume TV Show Command")
    @Test
    public void testPlexResumeTVShowCommand() {
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexResumeCommand.execute(null, null, null, configService, plexCommandConfig));
    }
    @DisplayName("Test Plex Stop Movie Command")
    @Test
    public void testPlexStopMovieCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        LocationResponseDto locationResponse = new LocationResponseDto();
        LocationResultDto locationResult = new LocationResultDto();
        locationResult.setLatitude(1);
        locationResult.setLongitude(1);
        locationResponse.setStatus(200);
        locationResponse.setResult(locationResult);

        SunriseSunsetResponseDto sunriseSunsetResponse = new SunriseSunsetResponseDto();
        SunriseSunsetResultDto sunriseSunsetResult = new SunriseSunsetResultDto();
        sunriseSunsetResponse.setStatus("200");
        sunriseSunsetResponse.setTzId("utc");
        sunriseSunsetResult.setSunset("18:00:00");
        sunriseSunsetResponse.setResult(sunriseSunsetResult);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationResponse);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetResponse);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Stop TV Show Command")
    @Test
    public void testPlexStopTVShowCommand() {
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexStopCommand.execute(null, null, null, configService, plexCommandConfig));
    }
    @DisplayName("Test Plex Pause Movie Command")
    @Test
    public void testPlexPauseMovieCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        LocationResponseDto locationResponse = new LocationResponseDto();
        LocationResultDto locationResult = new LocationResultDto();
        locationResult.setLatitude(1);
        locationResult.setLongitude(1);
        locationResponse.setStatus(200);
        locationResponse.setResult(locationResult);

        SunriseSunsetResponseDto sunriseSunsetResponse = new SunriseSunsetResponseDto();
        SunriseSunsetResultDto sunriseSunsetResult = new SunriseSunsetResultDto();
        sunriseSunsetResponse.setStatus("200");
        sunriseSunsetResponse.setTzId("utc");
        sunriseSunsetResult.setSunset("18:00:00");
        sunriseSunsetResponse.setResult(sunriseSunsetResult);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationResponse);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetResponse);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexPauseCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Pause TV Show Command")
    @Test
    public void testPlexPauseTVShowCommand() {
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexPauseCommand.execute(null, null, null, configService, plexCommandConfig));
    }
    @DisplayName("Test Plex Scrobble Movie Command")
    @Test
    public void testPlexScrobbleMovieCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        LocationResponseDto locationResponse = new LocationResponseDto();
        LocationResultDto locationResult = new LocationResultDto();
        locationResult.setLatitude(1);
        locationResult.setLongitude(1);
        locationResponse.setStatus(200);
        locationResponse.setResult(locationResult);

        SunriseSunsetResponseDto sunriseSunsetResponse = new SunriseSunsetResponseDto();
        SunriseSunsetResultDto sunriseSunsetResult = new SunriseSunsetResultDto();
        sunriseSunsetResponse.setStatus("200");
        sunriseSunsetResponse.setTzId("utc");
        sunriseSunsetResult.setSunset("18:00:00");
        sunriseSunsetResponse.setResult(sunriseSunsetResult);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationResponse);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetResponse);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE)).thenReturn(true);

        assertTrue(plexScrobbleCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Scrobble TV Show Command")
    @Test
    public void testPlexScrobbleTVShowCommand() {
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE)).thenReturn(false);

        assertFalse(plexScrobbleCommand.execute(null, null, null, configService, plexCommandConfig));
    }

    @DisplayName("Test Plex Play Movie Command returns false when command disabled by config")
    @Test
    public void testPlexPlayMovieCommandDisabledByConfig() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(plexCommandConfig.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE)).thenReturn(false);

        assertFalse(plexPlayCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Play Movie Command skips Hue when disabled")
    @Test
    public void testPlexPlayMovieCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPlayCommand = new PlexCommand(PlexEvent.PLAY, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexPlayCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Stop Movie Command skips Hue when disabled")
    @Test
    public void testPlexStopMovieCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexStopCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Pause Movie Command skips Hue when disabled")
    @Test
    public void testPlexPauseMovieCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexPauseCommand = new PlexCommand(PlexEvent.PAUSE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexPauseCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Resume Movie Command skips Hue when disabled")
    @Test
    public void testPlexResumeMovieCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexResumeCommand = new PlexCommand(PlexEvent.RESUME, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexResumeCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Scrobble Movie Command skips Hue when disabled")
    @Test
    public void testPlexScrobbleMovieCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        PlexCommand plexScrobbleCommand = new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertFalse(plexScrobbleCommand.execute(hueSceneController, null, null, configService, plexCommandConfig));
        verifyNoMoreInteractions(hueSceneController);
    }
}
