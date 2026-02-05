package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueLightStatusDto {
    private boolean on;

    @JsonSetter("on")
    public void setOn(boolean on) {
        this.on = on;
    }

    public boolean getOn()  { return this.on; }
}
