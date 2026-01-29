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

public class PlexPlayCommandTest {

    @DisplayName("Test Plex Play Command")
    @Test
    public void testPlexPlayCommand() {
        HueSceneController hueSceneController  = mock(HueSceneController.class);
        LocationController locationController = mock(LocationController.class);
        SunriseSunsetController sunriseSunsetController = mock(SunriseSunsetController.class);
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("media.play", "Good Will Hunting", 123);

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

        try {
            when(locationController.getLatitudeAndLogitude()).thenReturn(locationRootBean);
            when(sunriseSunsetController.getSunsetTimeForToday(1,1)).thenReturn(sunriseSunsetRootBean);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController);
        verify(hueSceneController).startMovieLightScene();
    }
}
