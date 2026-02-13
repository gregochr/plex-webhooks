package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HueLightStatusDto(boolean on) {
}
