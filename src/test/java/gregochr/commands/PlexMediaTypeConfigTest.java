package gregochr.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Plex Media Type Config Tests")
public class PlexMediaTypeConfigTest {

    @DisplayName("All events enabled when all flags are true")
    @Test
    public void testAllEventsEnabled() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(true, true, true, true, true);

        assertTrue(config.isEventEnabled(PlexEvent.PLAY));
        assertTrue(config.isEventEnabled(PlexEvent.RESUME));
        assertTrue(config.isEventEnabled(PlexEvent.STOP));
        assertTrue(config.isEventEnabled(PlexEvent.PAUSE));
        assertTrue(config.isEventEnabled(PlexEvent.SCROBBLE));
    }

    @DisplayName("All events disabled when all flags are false")
    @Test
    public void testAllEventsDisabled() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(false, false, false, false, false);

        assertFalse(config.isEventEnabled(PlexEvent.PLAY));
        assertFalse(config.isEventEnabled(PlexEvent.RESUME));
        assertFalse(config.isEventEnabled(PlexEvent.STOP));
        assertFalse(config.isEventEnabled(PlexEvent.PAUSE));
        assertFalse(config.isEventEnabled(PlexEvent.SCROBBLE));
    }

    @DisplayName("Play can be disabled independently")
    @Test
    public void testPlayDisabledIndependently() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(false, true, true, true, true);

        assertFalse(config.isEventEnabled(PlexEvent.PLAY));
        assertTrue(config.isEventEnabled(PlexEvent.RESUME));
        assertTrue(config.isEventEnabled(PlexEvent.STOP));
        assertTrue(config.isEventEnabled(PlexEvent.PAUSE));
        assertTrue(config.isEventEnabled(PlexEvent.SCROBBLE));
    }

    @DisplayName("Resume can be disabled independently")
    @Test
    public void testResumeDisabledIndependently() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(true, false, true, true, true);

        assertTrue(config.isEventEnabled(PlexEvent.PLAY));
        assertFalse(config.isEventEnabled(PlexEvent.RESUME));
    }

    @DisplayName("Stop can be disabled independently")
    @Test
    public void testStopDisabledIndependently() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(true, true, false, true, true);

        assertTrue(config.isEventEnabled(PlexEvent.PLAY));
        assertFalse(config.isEventEnabled(PlexEvent.STOP));
    }

    @DisplayName("Pause can be disabled independently")
    @Test
    public void testPauseDisabledIndependently() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(true, true, true, false, true);

        assertTrue(config.isEventEnabled(PlexEvent.PLAY));
        assertFalse(config.isEventEnabled(PlexEvent.PAUSE));
    }

    @DisplayName("Scrobble can be disabled independently")
    @Test
    public void testScrobbleDisabledIndependently() {
        PlexMediaTypeConfig config = new PlexMediaTypeConfig(true, true, true, true, false);

        assertTrue(config.isEventEnabled(PlexEvent.PLAY));
        assertFalse(config.isEventEnabled(PlexEvent.SCROBBLE));
    }
}
