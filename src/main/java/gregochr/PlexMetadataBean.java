package gregochr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlexMetadataBean {
    public String title;
    public int duration;

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

    public String toString() {
        return "Title: " + this.title + "\n"
                + "Duration: " + this.duration;
    }
}
