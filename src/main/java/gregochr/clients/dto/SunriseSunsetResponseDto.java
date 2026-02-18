package gregochr.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SunriseSunsetResponseDto(
        String status,
        String tzid,
        @JsonProperty("results") SunriseSunsetResultDto sunriseSunsetResultDto) {

    @Override
    public String toString() {
        return "[ Status: %s, tzid: %s, %s ]".formatted(status, tzid, sunriseSunsetResultDto);
    }
}
