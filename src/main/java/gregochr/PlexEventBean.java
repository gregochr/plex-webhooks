package gregochr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlexEventBean {
    public String event;

    @JsonSetter("event")
    public void setEvent(String event) {
        this.event = event;
    }

    public String getEvent()  { return this.event; }

    public String toString() {
        return "Event: " + event;
    }
}
