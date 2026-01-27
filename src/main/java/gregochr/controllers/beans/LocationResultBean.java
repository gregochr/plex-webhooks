package gregochr.controllers.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationResultBean {
    double longitude;
    double latitude;

    @JsonSetter("longitude")
    public void setTitle(double longitude) {
        this.longitude = longitude;
    }

    public double getLongitude()  { return this.longitude; }

    @JsonSetter("latitude")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLatitude()  { return this.latitude; }

    public String toString() {
        return "[ Latitude: " + this.latitude + ", "
                + "Longitude: " + this.longitude + " ]";
    }
}
