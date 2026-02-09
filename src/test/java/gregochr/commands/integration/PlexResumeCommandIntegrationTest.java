package gregochr.commands.integration;

import gregochr.commands.PlexResumeCommand;
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
public class PlexResumeCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Autowired
    private ConfigService configService;

    @Tag("integration")
    @DisplayName("Test Plex Resume Movie Command")
    @Test
    public void testPlexResumeMovieCommand() {
        PlexResumeCommand plexResumeCommand = new PlexResumeCommand("Good Will Hunting", 123, "movie");

        assert(plexResumeCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }

    @Tag("integration")
    @DisplayName("Test Plex Resume TV Show Command")
    @Test
    public void testPlexResumeTVShowCommand() {
        PlexResumeCommand plexResumeCommand = new PlexResumeCommand("Good Will Hunting", 123, "episode");

        assert(!plexResumeCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }

}
