package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlexPayloadDto(String event, @JsonProperty("Metadata") PlexMetadataDto metadata) {

    @Override
    public String toString() {
        return "[ Event: %s, %s ]".formatted(event, metadata);
    }
}
