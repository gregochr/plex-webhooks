package gregochr.commands;

import gregochr.services.config.PropertiesUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Plex Command Config Tests")
public class PlexCommandConfigTest {

    private static final PlexMediaTypeConfig ALL_ENABLED = new PlexMediaTypeConfig(true, true, true, true, true);
    private static final PlexMediaTypeConfig ALL_DISABLED = new PlexMediaTypeConfig(false, false, false, false, false);

    @DisplayName("Movie commands enabled when movie config is all enabled")
    @Test
    public void testMovieCommandsEnabled() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE));
    }

    @DisplayName("Movie commands disabled when movie config is all disabled")
    @Test
    public void testMovieCommandsDisabled() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.MOVIE, ALL_DISABLED));

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE));
    }

    @DisplayName("Episode commands enabled when episode config is all enabled")
    @Test
    public void testEpisodeCommandsEnabled() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.EPISODE, ALL_ENABLED));

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE));
    }

    @DisplayName("Episode commands disabled when episode config is all disabled")
    @Test
    public void testEpisodeCommandsDisabled() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.EPISODE, ALL_DISABLED));

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE));
    }

    @DisplayName("Movie and episode configs are independent")
    @Test
    public void testMovieAndEpisodeIndependent() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(
                PlexMediaType.MOVIE, ALL_ENABLED,
                PlexMediaType.EPISODE, ALL_DISABLED
        ));

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
    }

    @DisplayName("Returns false for media type not in config map")
    @Test
    public void testUnknownMediaTypeReturnsFalse() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
    }

    @DisplayName("Null media type returns false")
    @Test
    public void testNullMediaTypeReturnsFalse() {
        PlexCommandConfig config = new PlexCommandConfig(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, null));
    }

    @DisplayName("updateMediaType replaces config for a media type")
    @Test
    public void testUpdateMediaType() {
        PlexCommandConfig config = new PlexCommandConfig(new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED)));
        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));

        config.updateMediaType(PlexMediaType.MOVIE, ALL_DISABLED);

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
    }

    @DisplayName("onPropertiesUpdated updates config from event")
    @Test
    public void testOnPropertiesUpdated() {
        PlexCommandConfig config = new PlexCommandConfig(new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED)));
        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));

        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "plex.movie-play-enabled", "false",
                "plex.movie-resume-enabled", "false",
                "plex.movie-stop-enabled", "false",
                "plex.movie-pause-enabled", "false",
                "plex.movie-scrobble-enabled", "false"
        ));
        config.onPropertiesUpdated(event);

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE));
    }

    @DisplayName("onPropertiesUpdated preserves unchanged fields")
    @Test
    public void testOnPropertiesUpdatedPreservesUnchanged() {
        PlexCommandConfig config = new PlexCommandConfig(new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED)));

        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "plex.movie-play-enabled", "false"
        ));
        config.onPropertiesUpdated(event);

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE));
    }
}
