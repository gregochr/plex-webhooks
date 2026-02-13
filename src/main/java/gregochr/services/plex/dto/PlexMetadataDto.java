package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlexMetadataDto(String title, int duration, String type) {

    @Override
    public String toString() {
        return "[ Title: %s, Duration: %d, Type: %s ]".formatted(title, duration, type);
    }
}
