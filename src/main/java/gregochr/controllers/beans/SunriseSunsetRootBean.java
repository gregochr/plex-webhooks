package gregochr.controllers.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SunriseSunsetRootBean {
    public String status;
    public String tzid;
    public SunriseSunsetResultBean sunriseSunsetResultBean;

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
    public void setResult(SunriseSunsetResultBean sunriseSunsetResultBean) {
        this.sunriseSunsetResultBean = sunriseSunsetResultBean;
    }

    public SunriseSunsetResultBean getSunriseSunsetResultBean() {
        return sunriseSunsetResultBean;
    }

    @Override
    public String toString() {
        return "[ Status: " + this.status + ", " +
                "tzid: " + this.tzid + ", " +
                sunriseSunsetResultBean.toString() + " ]";
    }
}
