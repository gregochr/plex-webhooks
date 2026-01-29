package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlexPlayCommandIntegrationTest {

    @DisplayName("Test Plex Play Command")
    @Test
    public void testPlexPlayCommand() {
        HueSceneController hueSceneController = new HueSceneController();
        LocationController locationController = new LocationController();
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("media.play", "Good Will Hunting", 123);

        plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController);
    }
}
