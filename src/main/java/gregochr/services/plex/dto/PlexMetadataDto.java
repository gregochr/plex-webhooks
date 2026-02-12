package gregochr.services.plex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlexMetadataDto {
    public String title;
    public int duration;
    public String type;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle()  { return this.title; }

    @JsonSetter("duration")
    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDuration()  { return this.duration; }

    @JsonSetter("type")
    public void setType(String type) {
        this.type = type;
    }

    public String getType()  { return this.type; }

    @Override
    public String toString() {
        return String.format("[ Title: %s, Duration: %d, Type: %s ]",
                this.title, this.duration, this.type);
    }
}
