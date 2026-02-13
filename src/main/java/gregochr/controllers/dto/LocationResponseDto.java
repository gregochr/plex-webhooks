package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationResponseDto(int status, @JsonProperty("result") LocationResultDto locationResultDto) {

    @Override
    public String toString() {
        return "[ Status: %d, %s ]".formatted(status, locationResultDto);
    }
}
