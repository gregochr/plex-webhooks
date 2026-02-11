package gregochr.commands.integration;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
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
public class PlexStopCommandIntegrationTest {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private SunriseSunsetController sunriseSunsetController;

    @Autowired
    private ConfigService configService;

    @Autowired
    private PlexCommandConfig plexCommandConfig;

    @Tag("integration")
    @DisplayName("Test Plex Stop Movie Command")
    @Test
    public void testPlexStopMovieCommand() {
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        assertTrue(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
    }

    @Tag("integration")
    @DisplayName("Test Plex Stop TV Show Command")
    @Test
    public void testPlexStopTVShowCommand() {
        PlexCommand plexStopCommand = new PlexCommand(PlexEvent.STOP, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        assertFalse(plexStopCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService, plexCommandConfig));
    }
}
