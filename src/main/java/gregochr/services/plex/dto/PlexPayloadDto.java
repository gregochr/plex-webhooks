package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlexPayloadDto {
    public String event;
    public PlexMetadataDto metadata;

    @JsonSetter("event")
    public void setEvent(String event) {
        this.event = event;
    }
    public String getEvent()  { return this.event; }

    @JsonSetter("Metadata")
    public void setMetadata(PlexMetadataDto metadata) {
        this.metadata = metadata;
    }
    public PlexMetadataDto getMetadata()  { return this.metadata; }

    @Override
    public String toString() {
        return String.format("[ Event: %s, %s ]", event, this.metadata);
    }
}
