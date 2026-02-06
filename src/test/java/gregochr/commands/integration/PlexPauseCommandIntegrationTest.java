package gregochr.commands.integration;

import gregochr.commands.PlexPauseCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PlexPauseCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Autowired
    private ConfigService configService;

    @Tag("integration")
    @DisplayName("Test Plex Pause Movie Command")
    @Test
    public void testPlexStopMovieCommand() {
        PlexPauseCommand plexPauseCommand = new PlexPauseCommand("Good Will Hunting", 123, "movie");

        assert(plexPauseCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }

    @Tag("integration")
    @DisplayName("Test Plex Pause TV Show Command")
    @Test
    public void testPlexStopTVShowCommand() {
        PlexPauseCommand plexPauseCommand = new PlexPauseCommand("Good Will Hunting", 123, "episode");

        assert(plexPauseCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }
}
