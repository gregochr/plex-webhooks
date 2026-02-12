package gregochr.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SunriseSunsetResponseDto {
    public String status;
    public String tzid;
    public SunriseSunsetResultDto sunriseSunsetResultDto;

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus()  { return this.status; }

    @JsonSetter("tzid")
    public void setTzId(String tzId) {
        this.tzid = tzId;
    }

    public String getTzId()  { return this.tzid; }

    @JsonSetter("results")
    public void setResult(SunriseSunsetResultDto sunriseSunsetResultDto) {
        this.sunriseSunsetResultDto = sunriseSunsetResultDto;
    }

    public SunriseSunsetResultDto getSunriseSunsetResultDto() {
        return sunriseSunsetResultDto;
    }

    @Override
    public String toString() {
        return String.format("[ Status: %s, tzid: %s, %s ]",
                this.status, this.tzid, sunriseSunsetResultDto);
    }
}
