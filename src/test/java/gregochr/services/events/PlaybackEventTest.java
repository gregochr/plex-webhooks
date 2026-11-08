package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Playback Event Tests")
public class PlaybackEventTest {

    private static PlaybackEvent withEvent(String event) {
        return new PlaybackEvent(1, "2024-03-15T12:00:00Z", event, null, null, null, null, null, null, null,
                PlexOutcome.Kind.IGNORED_UNSUPPORTED, null, "plex", "{}");
    }

    @DisplayName("media.* events are media events")
    @Test
    public void mediaEventsAreMedia() {
        assertTrue(withEvent("media.play").isMedia());
        assertTrue(withEvent("media.rate").isMedia());
    }

    @DisplayName("library, admin and device events, and a missing name, are not media events")
    @Test
    public void otherEventsAreNotMedia() {
        assertFalse(withEvent("library.new").isMedia());
        assertFalse(withEvent("admin.database.backup").isMedia());
        assertFalse(withEvent("device.new").isMedia());
        assertFalse(withEvent("media").isMedia());
        assertFalse(withEvent(null).isMedia());
    }
}
