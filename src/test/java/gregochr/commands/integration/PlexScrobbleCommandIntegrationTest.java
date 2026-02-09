package gregochr.commands.integration;

import gregochr.commands.PlexScrobbleCommand;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class PlexScrobbleCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Autowired
    private ConfigService configService;

    @Tag("integration")
    @DisplayName("Test Plex Scrobble Movie Command")
    @Test
    public void testPlexScrobbleMovieCommand() {
        PlexScrobbleCommand plexScrobbleCommand = new PlexScrobbleCommand("Good Will Hunting", 123, "movie");

        assertTrue(plexScrobbleCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }

    @Tag("integration")
    @DisplayName("Test Plex Scrobble TV Show Command")
    @Test
    public void testPlexScrobbleTVShowCommand() {
        PlexScrobbleCommand plexScrobbleCommand = new PlexScrobbleCommand("Good Will Hunting", 123, "episode");

        assertFalse(plexScrobbleCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService));
    }
}
