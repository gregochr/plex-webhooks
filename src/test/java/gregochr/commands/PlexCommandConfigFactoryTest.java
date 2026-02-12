package gregochr.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Plex Command Config Factory Tests")
public class PlexCommandConfigFactoryTest {

    private Environment mockEnvironment() {
        Environment env = mock(Environment.class);
        when(env.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        return env;
    }

    @DisplayName("Factory reads movie properties from environment")
    @Test
    public void testFactoryReadsMovieProperties() {
        Environment env = mockEnvironment();
        when(env.getProperty("plex.movie-play-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.movie-resume-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.movie-stop-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.movie-pause-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.movie-scrobble-enabled", Boolean.class, false)).thenReturn(false);

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.MOVIE));
        assertTrue(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.MOVIE));
        assertFalse(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.MOVIE));
    }

    @DisplayName("Factory reads episode properties from environment")
    @Test
    public void testFactoryReadsEpisodeProperties() {
        Environment env = mockEnvironment();
        when(env.getProperty("plex.episode-play-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.episode-resume-enabled", Boolean.class, false)).thenReturn(false);
        when(env.getProperty("plex.episode-stop-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.episode-pause-enabled", Boolean.class, false)).thenReturn(false);
        when(env.getProperty("plex.episode-scrobble-enabled", Boolean.class, false)).thenReturn(true);

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE));
    }

    @DisplayName("Factory defaults to false when properties are not set")
    @Test
    public void testFactoryDefaultsToFalse() {
        Environment env = mockEnvironment();

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        for (PlexMediaType mediaType : PlexMediaType.values()) {
            for (PlexEvent event : PlexEvent.values()) {
                assertFalse(config.isCommandEnabled(event, mediaType));
            }
        }
    }

    @DisplayName("Factory uses correct property key pattern for all media types")
    @Test
    public void testFactoryPropertyKeyPattern() {
        Environment env = mockEnvironment();
        when(env.getProperty("plex.clip-play-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.track-pause-enabled", Boolean.class, false)).thenReturn(true);
        when(env.getProperty("plex.photo-scrobble-enabled", Boolean.class, false)).thenReturn(true);

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.CLIP));
        assertTrue(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.TRACK));
        assertTrue(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.PHOTO));
    }
}
