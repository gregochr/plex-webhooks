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

import static org.mockito.Mockito.*;

public class PlexCommandTest {

    @DisplayName("Test Plex Play Command")
    @Test
    public void testPlexPlayCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("Good Will Hunting", 123);

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

        assert(plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Resume Command")
    @Test
    public void testPlexResumeCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexResumeCommand plexResumeCommand = new PlexResumeCommand("Good Will Hunting", 123);

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

        assert(plexResumeCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Stop Command")
    @Test
    public void testPlexStopCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexStopCommand plexStopCommand = new PlexStopCommand("Good Will Hunting", 123);

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

        assert(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Pause Command")
    @Test
    public void testPlexPauseCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexPauseCommand plexPauseCommand = new PlexPauseCommand("Good Will Hunting", 123);

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

        assert(plexPauseCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Scrobble Command")
    @Test
    public void testPlexScrobbleCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexScrobbleCommand plexScrobbleCommand = new PlexScrobbleCommand("Good Will Hunting", 123);

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

        assert(plexScrobbleCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
        verify(hueSceneController).stopMovieLightScene();
    }

    @DisplayName("Test Plex Play Command skips Hue when disabled")
    @Test
    public void testPlexPlayCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("Good Will Hunting", 123);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assert(!plexPlayCommand.execute(hueSceneController, null, null, configService));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Stop Command skips Hue when disabled")
    @Test
    public void testPlexStopCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexStopCommand plexStopCommand = new PlexStopCommand("Good Will Hunting", 123);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assert(!plexStopCommand.execute(hueSceneController, null, null, configService));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Pause Command skips Hue when disabled")
    @Test
    public void testPlexPauseCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexPauseCommand plexPauseCommand = new PlexPauseCommand("Good Will Hunting", 123);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assert(!plexPauseCommand.execute(hueSceneController, null, null, configService));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Resume Command skips Hue when disabled")
    @Test
    public void testPlexResumeCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexResumeCommand plexResumeCommand = new PlexResumeCommand("Good Will Hunting", 123);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assert(!plexResumeCommand.execute(hueSceneController, null, null, configService));
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("Test Plex Scrobble Command skips Hue when disabled")
    @Test
    public void testPlexScrobbleCommandSkipsHueWhenDisabled() {
        HueSceneController hueSceneController = mock(HueSceneController.class);
        ConfigService configService = mock(ConfigService.class);
        PlexScrobbleCommand plexScrobbleCommand = new PlexScrobbleCommand("Good Will Hunting", 123);

        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assert(!plexScrobbleCommand.execute(hueSceneController, null, null, configService));
        verifyNoMoreInteractions(hueSceneController);
    }
}
