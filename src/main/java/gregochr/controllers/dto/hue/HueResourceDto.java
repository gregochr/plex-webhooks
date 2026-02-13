package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HueResourceDto(
        String id,
        @JsonProperty("id_v1") String idV1,
        String type,
        HueResourceReferenceDto owner,
        HueResourceMetadataDto metadata,
        List<HueResourceReferenceDto> services,
        List<HueResourceReferenceDto> children) {
}
