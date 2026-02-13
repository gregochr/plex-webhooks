package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HueResourceResponseDto(List<Object> errors, List<HueResourceDto> data) {
}
