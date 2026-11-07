package gregochr.services.events;

import com.fasterxml.jackson.annotation.JsonValue;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexOutcome;

import java.util.Objects;

/**
 * What is on the screen right now, as far as the Plex webhooks have told us. An immutable snapshot: each media
 * event produces the next one through {@link #apply}.
 *
 * <p>{@code sceneId} is the last scene CineLight activated, so a UI can show "the level this scene sets". It is not
 * a reading from the lights.
 *
 * @param state            see {@link State}
 * @param since            ISO-8601 instant of the event that entered the current state (or started the current
 *                         title); null while idle
 * @param mediaType        {@code movie}, {@code episode} and so on
 * @param title            the item title
 * @param grandparentTitle the show or artist
 * @param parentIndex      the season number
 * @param index            the episode number
 * @param year             the release year
 * @param player           the Plex player's device name
 * @param sceneId          the scene of the last {@code SCENE_APPLIED} outcome, or null if none yet
 */
public record NowPlaying(
        State state,
        String since,
        String mediaType,
        String title,
        String grandparentTitle,
        Integer parentIndex,
        Integer index,
        Integer year,
        String player,
        String sceneId) {

    /** The playback state, serialised as its label ({@code Playing}, {@code Paused}, {@code Stopped}, {@code Idle}). */
    public enum State {
        PLAYING("Playing"),
        PAUSED("Paused"),
        STOPPED("Stopped"),
        IDLE("Idle");

        private final String label;

        State(String label) {
            this.label = label;
        }

        @JsonValue
        public String label() {
            return label;
        }
    }

    /** The state before any media event has arrived. */
    public static NowPlaying idle() {
        return new NowPlaying(State.IDLE, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Returns the state after a media event.
     *
     * <ul>
     *   <li>An event CineLight does not support ({@code plexEvent} null, or the outcome is unsupported or an unknown
     *       media type) changes nothing.</li>
     *   <li>{@code play} and {@code resume} give {@code Playing}, {@code pause} gives {@code Paused}, {@code stop}
     *       gives {@code Stopped}. {@code since} restarts when the state changes or a different title starts.</li>
     *   <li>{@code scrobble} never changes the state: Plex sends it at about 90% watched while playback continues.
     *       It can still record a scene it applied.</li>
     * </ul>
     *
     * @param event     the event just recorded
     * @param plexEvent the recognised event the command carried, or null
     */
    public NowPlaying apply(PlaybackEvent event, PlexEvent plexEvent) {
        if (plexEvent == null || event.outcome() == PlexOutcome.Kind.IGNORED_UNSUPPORTED
                || event.outcome() == PlexOutcome.Kind.IGNORED_UNKNOWN_TYPE) {
            return this;
        }
        String scene = event.outcome() == PlexOutcome.Kind.SCENE_APPLIED ? event.sceneId() : sceneId;
        if (plexEvent == PlexEvent.SCROBBLE) {
            return new NowPlaying(state, since, mediaType, title, grandparentTitle, parentIndex, index, year,
                    player, scene);
        }
        State next = switch (plexEvent) {
            case PLAY, RESUME -> State.PLAYING;
            case PAUSE -> State.PAUSED;
            case STOP, SCROBBLE -> State.STOPPED;
        };
        String entered = next != state || !Objects.equals(title, event.title()) ? event.timestamp() : since;
        return new NowPlaying(next, entered, event.mediaType(), event.title(), event.grandparentTitle(),
                event.parentIndex(), event.index(), event.year(), event.player(), scene);
    }
}
