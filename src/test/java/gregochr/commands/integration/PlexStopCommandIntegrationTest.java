package gregochr.commands.integration;

import gregochr.commands.PlexStopCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class PlexStopCommandIntegrationTest {
    @Tag("integration")
    @DisplayName("Test Plex Stop Command")
    @Test
    public void testPlexStopCommand() {
        HueSceneController hueSceneController = new HueSceneController();
        LocationController locationController = new LocationController();
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();
        PlexStopCommand plexStopCommand = new PlexStopCommand("media.stop", "Good Will Hunting", 123);

        assert(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController));
    }
}
