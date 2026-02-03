package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.LocationResultBean;
import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetResultBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class PlexCommandTest {

    @DisplayName("Test Plex Play Command")
    @Test
    public void testPlexPlayCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("Good Will Hunting", 123);

        LocationRootBean locationRootBean = new LocationRootBean();
        LocationResultBean locationResultBean = new LocationResultBean();
        locationResultBean.setLatitude(1);
        locationResultBean.setLongitude(1);
        locationRootBean.setStatus(200);
        locationRootBean.setResult(locationResultBean);

        SunriseSunsetRootBean sunriseSunsetRootBean = new SunriseSunsetRootBean();
        SunriseSunsetResultBean sunriseSunsetResultBean = new SunriseSunsetResultBean();
        sunriseSunsetRootBean.setStatus("200");
        sunriseSunsetRootBean.setTzId("utc");
        sunriseSunsetResultBean.setSunset("18:00:00");
        sunriseSunsetRootBean.setResult(sunriseSunsetResultBean);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);

        assert(plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Resume Command")
    @Test
    public void testPlexResumeCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexResumeCommand plexResumeCommand = new PlexResumeCommand("Good Will Hunting", 123);

        LocationRootBean locationRootBean = new LocationRootBean();
        LocationResultBean locationResultBean = new LocationResultBean();
        locationResultBean.setLatitude(1);
        locationResultBean.setLongitude(1);
        locationRootBean.setStatus(200);
        locationRootBean.setResult(locationResultBean);

        SunriseSunsetRootBean sunriseSunsetRootBean = new SunriseSunsetRootBean();
        SunriseSunsetResultBean sunriseSunsetResultBean = new SunriseSunsetResultBean();
        sunriseSunsetRootBean.setStatus("200");
        sunriseSunsetRootBean.setTzId("utc");
        sunriseSunsetResultBean.setSunset("18:00:00");
        sunriseSunsetRootBean.setResult(sunriseSunsetResultBean);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        when(hueSceneController.startMovieLightScene()).thenReturn(true);

        assert(plexResumeCommand.execute(hueSceneController, locationController, sunriseSunsetController));
        verify(hueSceneController).startMovieLightScene();
    }
    @DisplayName("Test Plex Stop Command")
    @Test
    public void testPlexStopCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexStopCommand plexStopCommand = new PlexStopCommand("Good Will Hunting", 123);

        LocationRootBean locationRootBean = new LocationRootBean();
        LocationResultBean locationResultBean = new LocationResultBean();
        locationResultBean.setLatitude(1);
        locationResultBean.setLongitude(1);
        locationRootBean.setStatus(200);
        locationRootBean.setResult(locationResultBean);

        SunriseSunsetRootBean sunriseSunsetRootBean = new SunriseSunsetRootBean();
        SunriseSunsetResultBean sunriseSunsetResultBean = new SunriseSunsetResultBean();
        sunriseSunsetRootBean.setStatus("200");
        sunriseSunsetRootBean.setTzId("utc");
        sunriseSunsetResultBean.setSunset("18:00:00");
        sunriseSunsetRootBean.setResult(sunriseSunsetResultBean);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        assert(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Pause Command")
    @Test
    public void testPlexPauseCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexPauseCommand plexPauseCommand = new PlexPauseCommand("Good Will Hunting", 123);

        LocationRootBean locationRootBean = new LocationRootBean();
        LocationResultBean locationResultBean = new LocationResultBean();
        locationResultBean.setLatitude(1);
        locationResultBean.setLongitude(1);
        locationRootBean.setStatus(200);
        locationRootBean.setResult(locationResultBean);

        SunriseSunsetRootBean sunriseSunsetRootBean = new SunriseSunsetRootBean();
        SunriseSunsetResultBean sunriseSunsetResultBean = new SunriseSunsetResultBean();
        sunriseSunsetRootBean.setStatus("200");
        sunriseSunsetRootBean.setTzId("utc");
        sunriseSunsetResultBean.setSunset("18:00:00");
        sunriseSunsetRootBean.setResult(sunriseSunsetResultBean);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        assert(plexPauseCommand.execute(hueSceneController, locationController, sunriseSunsetController));
        verify(hueSceneController).stopMovieLightScene();
    }
    @DisplayName("Test Plex Scrobble Command")
    @Test
    public void testPlexScrobbleCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexScrobbleCommand plexScrobbleCommand = new PlexScrobbleCommand("Good Will Hunting", 123);

        LocationRootBean locationRootBean = new LocationRootBean();
        LocationResultBean locationResultBean = new LocationResultBean();
        locationResultBean.setLatitude(1);
        locationResultBean.setLongitude(1);
        locationRootBean.setStatus(200);
        locationRootBean.setResult(locationResultBean);

        SunriseSunsetRootBean sunriseSunsetRootBean = new SunriseSunsetRootBean();
        SunriseSunsetResultBean sunriseSunsetResultBean = new SunriseSunsetResultBean();
        sunriseSunsetRootBean.setStatus("200");
        sunriseSunsetRootBean.setTzId("utc");
        sunriseSunsetResultBean.setSunset("18:00:00");
        sunriseSunsetRootBean.setResult(sunriseSunsetResultBean);

        when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
        when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        when(hueSceneController.stopMovieLightScene()).thenReturn(true);

        assert(plexScrobbleCommand.execute(hueSceneController, locationController, sunriseSunsetController));
        verify(hueSceneController).stopMovieLightScene();
    }
}
