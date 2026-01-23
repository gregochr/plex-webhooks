package gregochr.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlexRootBean {
    public String event;
    public PlexMetadataBean metadata;

    @JsonSetter("event")
    public void setEvent(String event) {
        this.event = event;
    }
    public String getEvent()  { return this.event; }

    @JsonSetter("Metadata")
    public void setMetadata(PlexMetadataBean metadata) {
        this.metadata = metadata;
    }
    public PlexMetadataBean getMetadata()  { return this.metadata; }

    public String toString() {
        return "[ Event: " + event  + ", "
                + this.metadata.toString() + " ]";
    }
}
