package gregochr.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Plex Command Config Factory Tests")
public class PlexCommandConfigFactoryTest {

    private static String key(PlexMediaType mediaType, PlexEvent event) {
        return "plex." + mediaType.getType() + "-" + event.name().toLowerCase() + "-enabled";
    }

    @DisplayName("Factory reads movie properties from environment")
    @Test
    public void testFactoryReadsMovieProperties() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "true")
                .withProperty("plex.movie-resume-enabled", "true")
                .withProperty("plex.movie-stop-enabled", "true")
                .withProperty("plex.movie-pause-enabled", "true")
                .withProperty("plex.movie-scrobble-enabled", "false");

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
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.episode-play-enabled", "true")
                .withProperty("plex.episode-resume-enabled", "false")
                .withProperty("plex.episode-stop-enabled", "true")
                .withProperty("plex.episode-pause-enabled", "false")
                .withProperty("plex.episode-scrobble-enabled", "true");

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.RESUME, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.STOP, PlexMediaType.EPISODE));
        assertFalse(config.isCommandEnabled(PlexEvent.PAUSE, PlexMediaType.EPISODE));
        assertTrue(config.isCommandEnabled(PlexEvent.SCROBBLE, PlexMediaType.EPISODE));
    }

    @DisplayName("Factory defaults every event and media type to disabled when no property is set")
    @Test
    public void testFactoryDefaultsToFalse() {
        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(new MockEnvironment());

        for (PlexMediaType mediaType : PlexMediaType.values()) {
            for (PlexEvent event : PlexEvent.values()) {
                assertFalse(config.isCommandEnabled(event, mediaType), key(mediaType, event));
            }
        }
    }

    @DisplayName("Factory reads TRUE in upper case as enabled")
    @Test
    public void testFactoryUpperCaseTrue() {
        MockEnvironment env = new MockEnvironment().withProperty("plex.movie-play-enabled", "TRUE");

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
    }

    @DisplayName("Factory reads FALSE in upper case as disabled")
    @Test
    public void testFactoryUpperCaseFalse() {
        MockEnvironment env = new MockEnvironment().withProperty("plex.movie-play-enabled", "FALSE");

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
    }

    @DisplayName("Factory reads yes, on, 1 and padded true as enabled, matching Spring")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"yes", "on", "1", " true ", "YES", "On"})
    public void testFactoryAcceptsSpringTrueValues(String value) {
        MockEnvironment env = new MockEnvironment().withProperty("plex.movie-play-enabled", value);

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertTrue(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
    }

    @DisplayName("Factory reads no, off, 0, empty and garbage as disabled")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"no", "off", "0", "", "maybe", "truee"})
    public void testFactoryRejectsOtherValues(String value) {
        MockEnvironment env = new MockEnvironment().withProperty("plex.movie-play-enabled", value);

        PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

        assertFalse(config.isCommandEnabled(PlexEvent.PLAY, PlexMediaType.MOVIE));
    }

    @DisplayName("Factory enabling one property enables exactly that event for exactly that media type")
    @ParameterizedTest(name = "only {0} play")
    @EnumSource(PlexMediaType.class)
    public void testFactoryEnablesOnlyTheSetFlag(PlexMediaType enabledType) {
        for (PlexEvent enabledEvent : PlexEvent.values()) {
            MockEnvironment env = new MockEnvironment().withProperty(key(enabledType, enabledEvent), "true");

            PlexCommandConfig config = new PlexCommandConfigFactory().plexCommandConfig(env);

            for (PlexMediaType type : PlexMediaType.values()) {
                for (PlexEvent event : PlexEvent.values()) {
                    boolean expected = type == enabledType && event == enabledEvent;
                    assertEquals(expected, config.isCommandEnabled(event, type),
                            "flag " + key(enabledType, enabledEvent) + " checked at " + key(type, event));
                }
            }
        }
    }
}
