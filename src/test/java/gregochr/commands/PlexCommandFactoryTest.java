package gregochr.commands;

import gregochr.services.config.ConfigService;
import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlexCommandFactoryTest {
    @DisplayName("Test Create Plex Play Movie Command")
    @Test
    public void testCreatePlexPlayMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPlayCommand);
        assert(plexCommand.getEvent().equals("media.play"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("movie"));
    }

    @DisplayName("Test Create Plex Play TV Show Command")
    @Test
    public void testCreatePlexPlayTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPlayCommand);
        assert(plexCommand.getEvent().equals("media.play"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("episode"));
    }

    @DisplayName("Test Create Plex Resume Movie Command")
    @Test
    public void testCreatePlexResumeMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.resume", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexResumeCommand);
        assert(plexCommand.getEvent().equals("media.resume"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("movie"));
    }
    @DisplayName("Test Create Plex Resume TV Show Command")
    @Test
    public void testCreatePlexResumeTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.resume", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexResumeCommand);
        assert(plexCommand.getEvent().equals("media.resume"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("episode"));
    }

    @DisplayName("Test Create Plex Stop Movie Command")
    @Test
    public void testCreatePlexStopMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.stop", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexStopCommand);
        assert(plexCommand.getEvent().equals("media.stop"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("movie"));
    }

    @DisplayName("Test Create Plex Stop TV Show Command")
    @Test
    public void testCreatePlexStopTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.stop", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexStopCommand);
        assert(plexCommand.getEvent().equals("media.stop"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("episode"));
    }

    @DisplayName("Test Create Plex Pause Movie Command")
    @Test
    public void testCreatePlexPauseMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.pause", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPauseCommand);
        assert(plexCommand.getEvent().equals("media.pause"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("movie"));
    }

    @DisplayName("Test Create Plex Pause TV Show Command")
    @Test
    public void testCreatePlexPauseTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.pause", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPauseCommand);
        assert(plexCommand.getEvent().equals("media.pause"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("episode"));
    }

    @DisplayName("Test Create Plex Scrobble Movie Command")
    @Test
    public void testCreatePlexScrobbleMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.scrobble", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexScrobbleCommand);
        assert(plexCommand.getEvent().equals("media.scrobble"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("movie"));
    }

    @DisplayName("Test Create Plex Scrobble TV Show Command")
    @Test
    public void testCreatePlexScrobbleTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.scrobble", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexScrobbleCommand);
        assert(plexCommand.getEvent().equals("media.scrobble"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
        assert(plexCommand.getType().equals("episode"));
    }

    @DisplayName("Test unknown plex event creates a NoOp command")
    @Test
    public void testPlexUnknownEvent() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("RandomEventString", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexNoOpCommand);
        assert(plexCommand.getEvent().equals("RandomEventString"));
        assert(plexCommand.getType().equals("movie"));
    }

    @DisplayName("Test unknown plex type creates a NoOp command")
    @Test
    public void testPlexUnknownType() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "RandomTypeString");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexNoOpCommand);
        assert(plexCommand.getEvent().equals("media.play"));
        assert(plexCommand.getType().equals("RandomTypeString"));
    }

    @DisplayName("Test NoOp command execute returns false")
    @Test
    public void testNoOpCommandExecuteReturnsFalse() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("unknown.event", "movie");
        ConfigService configService = mock(ConfigService.class);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        boolean result = plexCommand.execute(null, null, null, configService);

        assert(!result);
    }

    @Nonnull
    private static PlexPayloadDto getPlexPayloadDto(String event, String type) {
        PlexPayloadDto plexPayload = new PlexPayloadDto();
        plexPayload.setEvent(event);

        PlexMetadataDto plexMetadata = new PlexMetadataDto();
        plexMetadata.setTitle("Good Will Hunting");
        plexMetadata.setDuration(123);
        plexMetadata.setType(type);
        plexPayload.setMetadata(plexMetadata);
        return plexPayload;
    }
}
