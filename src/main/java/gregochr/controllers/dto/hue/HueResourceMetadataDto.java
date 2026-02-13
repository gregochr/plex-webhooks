package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HueResourceMetadataDto(String name, String archetype, String category) {
}
