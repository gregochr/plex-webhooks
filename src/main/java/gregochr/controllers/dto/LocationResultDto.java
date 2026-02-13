package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationResultDto(double longitude, double latitude) {

    @Override
    public String toString() {
        return "[ Latitude: %s, Longitude: %s ]".formatted(latitude, longitude);
    }
}
