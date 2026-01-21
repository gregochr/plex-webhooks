package gregochr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationRootBean {
    public int status;
    public LocationResultBean locationResultBean;

    @JsonSetter("status")
    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus()  { return this.status; }

    @JsonSetter("result")
    public void setResult(LocationResultBean locationResultBean) {
        this.locationResultBean = locationResultBean;
    }

    public LocationResultBean getLocationResultBean() {
        return locationResultBean;
    }

    public String toString() {
        return "Status: " + Integer.toString(status) + "\n" +
                locationResultBean.toString();
    }
}
