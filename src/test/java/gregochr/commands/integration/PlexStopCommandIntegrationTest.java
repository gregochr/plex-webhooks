package gregochr.commands.integration;

import gregochr.commands.PlexStopCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PlexStopCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Tag("integration")
    @DisplayName("Test Plex Stop Command")
    @Test
    public void testPlexStopCommand() {
        PlexStopCommand plexStopCommand = new PlexStopCommand("Good Will Hunting", 123);

        assert(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController));
    }
}
