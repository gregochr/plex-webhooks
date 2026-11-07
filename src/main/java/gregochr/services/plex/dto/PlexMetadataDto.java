package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The parts of a Plex {@code Metadata} object CineLight reads. Everything else Plex sends is ignored.
 *
 * @param title            the item title (an episode's own title, a track's title)
 * @param duration         length in milliseconds; 0 when Plex omits it
 * @param type             {@code movie}, {@code episode}, {@code track} and so on
 * @param grandparentTitle the show or artist for an episode or track
 * @param parentIndex      the season number for an episode
 * @param index            the episode number
 * @param year             the release year
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlexMetadataDto(String title, int duration, String type, String grandparentTitle,
        Integer parentIndex, Integer index, Integer year) {

    public PlexMetadataDto(String title, int duration, String type) {
        this(title, duration, type, null, null, null, null);
    }

    @Override
    public String toString() {
        return "[ Title: %s, Duration: %d, Type: %s ]".formatted(title, duration, type);
    }
}
