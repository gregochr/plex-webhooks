package gregochr.commands;

import gregochr.services.config.PropertiesUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @DisplayName("onPropertiesUpdated leaves other media types' entries untouched and adds none")
    @Test
    public void testOnPropertiesUpdatedOnlyTouchesUpdatedMediaType() {
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));
        PlexCommandConfig config = new PlexCommandConfig(configs);

        config.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("plex.episode-play-enabled", "true")));

        assertEquals(Map.of(
                PlexMediaType.MOVIE, ALL_ENABLED,
                PlexMediaType.EPISODE, new PlexMediaTypeConfig(true, false, false, false, false)), configs);
    }

    @DisplayName("onPropertiesUpdated ignores a key that only shares the start of a media type's prefix")
    @Test
    public void testOnPropertiesUpdatedPrefixBoundary() {
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));
        PlexCommandConfig config = new PlexCommandConfig(configs);

        config.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of(
                "plex.movie", "false", "plex.movies-play-enabled", "false", "movie-play-enabled", "false")));

        assertEquals(Map.of(PlexMediaType.MOVIE, ALL_ENABLED), configs);
    }

    @DisplayName("onPropertiesUpdated reads true, on, yes and 1 (any case, trimmed) as true and anything else as false")
    @Test
    public void testOnPropertiesUpdatedParsesBooleans() {
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_DISABLED));
        PlexCommandConfig config = new PlexCommandConfig(configs);

        config.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of(
                "plex.movie-play-enabled", "TRUE",
                "plex.movie-resume-enabled", "yes",
                "plex.movie-stop-enabled", "",
                "plex.movie-pause-enabled", " on ",
                "plex.movie-scrobble-enabled", "1")));

        assertEquals(new PlexMediaTypeConfig(true, true, false, true, true), configs.get(PlexMediaType.MOVIE));
    }

    @DisplayName("onPropertiesUpdated reads no, off, 0 and garbage as false")
    @Test
    public void testOnPropertiesUpdatedRejectsNonTrue() {
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(Map.of(PlexMediaType.MOVIE, ALL_ENABLED));
        PlexCommandConfig config = new PlexCommandConfig(configs);

        config.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of(
                "plex.movie-play-enabled", "no",
                "plex.movie-resume-enabled", "off",
                "plex.movie-stop-enabled", "0",
                "plex.movie-pause-enabled", "garbage",
                "plex.movie-scrobble-enabled", "")));

        assertEquals(ALL_DISABLED, configs.get(PlexMediaType.MOVIE));
    }

    @DisplayName("onPropertiesUpdated starts an unconfigured media type from all disabled")
    @Test
    public void testOnPropertiesUpdatedUnconfiguredMediaType() {
        PlexCommandConfig config = new PlexCommandConfig(new EnumMap<>(PlexMediaType.class));

        config.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("plex.photo-stop-enabled", "true")));

        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.PHOTO));
        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.PHOTO));
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
