package gregochr.commands;

import gregochr.services.config.ConfigService;
import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlexCommandFactoryTest {
    @DisplayName("Test Create Plex Play Movie Command")
    @Test
    public void testCreatePlexPlayMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.PLAY, plexCommand.getPlexEvent());
        assertEquals("media.play", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Play TV Show Command")
    @Test
    public void testCreatePlexPlayTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.PLAY, plexCommand.getPlexEvent());
        assertEquals("media.play", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.EPISODE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Resume Movie Command")
    @Test
    public void testCreatePlexResumeMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.resume", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.RESUME, plexCommand.getPlexEvent());
        assertEquals("media.resume", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }
    @DisplayName("Test Create Plex Resume TV Show Command")
    @Test
    public void testCreatePlexResumeTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.resume", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.RESUME, plexCommand.getPlexEvent());
        assertEquals("media.resume", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.EPISODE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Stop Movie Command")
    @Test
    public void testCreatePlexStopMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.stop", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.STOP, plexCommand.getPlexEvent());
        assertEquals("media.stop", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Stop TV Show Command")
    @Test
    public void testCreatePlexStopTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.stop", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.STOP, plexCommand.getPlexEvent());
        assertEquals("media.stop", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.EPISODE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Pause Movie Command")
    @Test
    public void testCreatePlexPauseMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.pause", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.PAUSE, plexCommand.getPlexEvent());
        assertEquals("media.pause", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Pause TV Show Command")
    @Test
    public void testCreatePlexPauseTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.pause", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.PAUSE, plexCommand.getPlexEvent());
        assertEquals("media.pause", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.EPISODE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Scrobble Movie Command")
    @Test
    public void testCreatePlexScrobbleMovieCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.scrobble", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.SCROBBLE, plexCommand.getPlexEvent());
        assertEquals("media.scrobble", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }

    @DisplayName("Test Create Plex Scrobble TV Show Command")
    @Test
    public void testCreatePlexScrobbleTVShowCommand() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.scrobble", "episode");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertEquals(PlexEvent.SCROBBLE, plexCommand.getPlexEvent());
        assertEquals("media.scrobble", plexCommand.getEvent());
        assertEquals("Good Will Hunting", plexCommand.getTitle());
        assertEquals(123, plexCommand.getDuration());
        assertEquals(PlexMediaType.EPISODE, plexCommand.getMediaType());
    }

    @DisplayName("Test unknown plex event creates a NoOp command")
    @Test
    public void testPlexUnknownEvent() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("RandomEventString", "movie");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertNull(plexCommand.getPlexEvent());
        assertEquals("RandomEventString", plexCommand.getEvent());
        assertEquals(PlexMediaType.MOVIE, plexCommand.getMediaType());
    }

    @DisplayName("Test unknown plex type creates a NoOp command")
    @Test
    public void testPlexUnknownType() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("media.play", "RandomTypeString");

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        assertNotNull(plexCommand);
        assertNull(plexCommand.getPlexEvent());
        assertEquals("media.play", plexCommand.getEvent());
        assertNull(plexCommand.getMediaType());
    }

    @DisplayName("Test NoOp command execute returns false")
    @Test
    public void testNoOpCommandExecuteReturnsFalse() {
        PlexPayloadDto plexPayload = getPlexPayloadDto("unknown.event", "movie");
        ConfigService configService = mock(ConfigService.class);
        PlexCommandConfig plexCommandConfig = mock(PlexCommandConfig.class);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        PlexCommandFactory factory = new PlexCommandFactory(plexPayload);
        PlexCommand plexCommand = factory.getPlexCommand();

        boolean result = plexCommand.execute(null, configService, plexCommandConfig);

        assertFalse(result);
    }

    @Nonnull
    private static PlexPayloadDto getPlexPayloadDto(String event, String type) {
        PlexMetadataDto plexMetadata = new PlexMetadataDto("Good Will Hunting", 123, type);
        return new PlexPayloadDto(event, plexMetadata);
    }
}
