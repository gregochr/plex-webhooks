package gregochr.commands.integration;

import gregochr.commands.PlexPlayCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class PlexPlayCommandIntegrationTest {
    @Tag("integration")
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
