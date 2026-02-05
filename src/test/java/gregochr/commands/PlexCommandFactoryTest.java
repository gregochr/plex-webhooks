package gregochr.commands;

import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlexCommandFactoryTest {
    @DisplayName("Test Create Plex Play Command")
    @Test
    public void testCreatePlexPlayCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPlayCommand);
        assert(plexCommand.getEvent().equals("media.play"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Resume Command")
    @Test
    public void testCreatePlexResumeCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.resume");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexResumeCommand);
        assert(plexCommand.getEvent().equals("media.resume"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Stop Command")
    @Test
    public void testCreatePlexStopCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.stop");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexStopCommand);
        assert(plexCommand.getEvent().equals("media.stop"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Pause Command")
    @Test
    public void testCreatePlexPauseCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.pause");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPauseCommand);
        assert(plexCommand.getEvent().equals("media.pause"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Scrobble Command")
    @Test
    public void testCreatePlexScrobbleCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.scrobble");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexScrobbleCommand);
        assert(plexCommand.getEvent().equals("media.scrobble"));
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test unknown plex event creates a NoOp command")
    @Test
    public void testPlexUnknownEvent() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("RandomEventString");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexNoOpCommand);
        assert(plexCommand.getEvent().equals("RandomEventString"));
    }

    @DisplayName("Test NoOp command execute returns false")
    @Test
    public void testNoOpCommandExecuteReturnsFalse() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("unknown.event");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        boolean result = plexCommand.execute(null, null, null, null);

        assert(!result);
    }

    @Nonnull
    private static PlexPayloadDto getPlexPayloadDto(String event) {
        PlexPayloadDto plexPayload = new PlexPayloadDto();
        plexPayload.setEvent(event);

        PlexMetadataDto plexMetadata = new PlexMetadataDto();
        plexMetadata.setTitle("Good Will Hunting");
        plexMetadata.setDuration(123);
        plexPayload.setMetadata(plexMetadata);
        return plexPayload;
    }
}
