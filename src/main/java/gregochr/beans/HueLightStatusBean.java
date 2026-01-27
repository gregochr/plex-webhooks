package gregochr.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueLightStatusBean {
    private boolean on;

    @JsonSetter("oo")
    public void setOn(boolean title) {
        this.on = on;
    }

    public boolean getOn()  { return this.on; }
}
