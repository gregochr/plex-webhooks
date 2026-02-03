package gregochr.commands.integration;

import gregochr.commands.PlexPlayCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PlexPlayCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Tag("integration")
    @DisplayName("Test Plex Play Command")
    @Test
    public void testPlexPlayCommand() {
        PlexPlayCommand plexPlayCommand = new PlexPlayCommand("Good Will Hunting", 123);

        assert(plexPlayCommand.execute(hueSceneController, locationController, sunriseSunsetController));
    }
}
