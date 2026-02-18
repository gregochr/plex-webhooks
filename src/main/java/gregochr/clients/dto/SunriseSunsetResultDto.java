package gregochr.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SunriseSunsetResultDto(String sunrise, String sunset) {

    @Override
    public String toString() {
        return "[ Sunrise: %s, Sunset: %s ]".formatted(sunrise, sunset);
    }
}
