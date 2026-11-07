package gregochr.services.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRawValue;
import gregochr.commands.PlexOutcome;

/**
 * One Plex (or Debug) event together with what CineLight did about it. It is the body of the SSE {@code message}
 * event and of the history endpoint.
 *
 * @param id               monotonic across the life of the process
 * @param timestamp        ISO-8601 instant; a String because the SSE mapper has no JavaTimeModule
 * @param event            the Plex event name, for example {@code media.play}
 * @param mediaType        {@code movie}, {@code episode} and so on; null when Plex sent none
 * @param title            the item title
 * @param grandparentTitle the show or artist
 * @param parentIndex      the season number
 * @param index            the episode number
 * @param year             the release year
 * @param player           the Plex player's device name
 * @param outcome          what happened to the lights
 * @param sceneId          the scene activated, when the outcome is {@code SCENE_APPLIED}
 * @param source           {@code plex} or {@code debug}
 * @param payload          the sanitised Plex payload as raw JSON (see {@link PlexPayloadSanitizer})
 * @param command          the legacy {@code {event, title, duration}} object the Thymeleaf dashboard reads; to be
 *                         removed when that dashboard is retired
 */
public record PlaybackEvent(
        long id,
        String timestamp,
        String event,
        String mediaType,
        String title,
        String grandparentTitle,
        Integer parentIndex,
        Integer index,
        Integer year,
        String player,
        PlexOutcome.Kind outcome,
        String sceneId,
        String source,
        @JsonRawValue String payload,
        Command command) {

    /** The legacy command summary. */
    public record Command(String event, String title, int duration) {
    }

    /** True for {@code media.*} events: the only ones that are stored in the history. */
    @JsonIgnore
    public boolean isMedia() {
        return event != null && event.startsWith("media.");
    }
}
