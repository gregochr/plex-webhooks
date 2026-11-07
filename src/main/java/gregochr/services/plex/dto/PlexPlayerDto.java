package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The parts of a Plex {@code Player} object CineLight reads: the device name only. The player's address and uuid
 * are deliberately not read.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlexPlayerDto(String title) {
}
