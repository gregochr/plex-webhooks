package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationResultDto {
    double longitude;
    double latitude;

    @JsonSetter("longitude")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLongitude()  { return this.longitude; }

    @JsonSetter("latitude")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLatitude()  { return this.latitude; }

    @Override
    public String toString() {
        return "[ Latitude: " + this.latitude + ", "
                + "Longitude: " + this.longitude + " ]";
    }
}
