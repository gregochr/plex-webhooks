package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SunriseSunsetResultDto {
    String sunrise;
    String sunset;

    @JsonSetter("sunrise")
    public void setSunrise(String sunrise) {
        this.sunrise = sunrise;
    }

    public String getSunrise()  { return this.sunrise; }

    @JsonSetter("sunset")
    public void setSunset(String sunset) {
        this.sunset = sunset;
    }

    public String getSunset()  { return this.sunset; }

    @Override
    public String toString() {
        return "[ Sunrise: " + this.sunrise + ", "
                + "Sunset: " + this.sunset + " ]";
    }
}
