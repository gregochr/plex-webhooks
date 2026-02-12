package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationResponseDto {
    public int status;
    public LocationResultDto locationResultDto;

    @JsonSetter("status")
    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus()  { return this.status; }

    @JsonSetter("result")
    public void setResult(LocationResultDto locationResultDto) {
        this.locationResultDto = locationResultDto;
    }

    public LocationResultDto getLocationResultDto() {
        return locationResultDto;
    }

    @Override
    public String toString() {
        return String.format("[ Status: %d, %s ]", status, locationResultDto);
    }
}
