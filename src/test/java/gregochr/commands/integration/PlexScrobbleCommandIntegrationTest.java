package gregochr.commands.integration;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class PlexScrobbleCommandIntegrationTest extends PlexCommandIntegrationTestBase {

    @Autowired
    private HueSceneController hueSceneController;

    @Autowired
    private ConfigService configService;

    @Autowired
    private PlexCommandConfig plexCommandConfig;

    @Tag("integration")
    @DisplayName("Test Plex Scrobble Movie Command")
    @Test
    public void testPlexScrobbleMovieCommand() {
        PlexCommand plexScrobbleCommand =
                new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.MOVIE);

        assertFalse(plexScrobbleCommand.execute(hueSceneController, configService, plexCommandConfig));
    }

    @Tag("integration")
    @DisplayName("Test Plex Scrobble TV Show Command")
    @Test
    public void testPlexScrobbleTVShowCommand() {
        PlexCommand plexScrobbleCommand =
                new PlexCommand(PlexEvent.SCROBBLE, "Good Will Hunting", 123, PlexMediaType.EPISODE);

        assertFalse(plexScrobbleCommand.execute(hueSceneController, configService, plexCommandConfig));
    }
}
