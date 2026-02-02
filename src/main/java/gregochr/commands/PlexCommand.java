package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;

public abstract class PlexCommand {
    protected String event;
    protected String title;
    protected int duration;

    public PlexCommand(String event, String title, int duration) {
        this.event = event;
        this.title  = title;
        this.duration = duration;
    }

    public String getEvent() {
        return this.event;
    }
    public String getTitle() {
        return this.title;
    }
    public int getDuration() { return this.duration; }

    public String toString() {
        return "[ Event: " + this.event + ", " +
                "Title: " + this.title + ", " +
                "Duration: " + this.duration + " ]";
    }

    abstract public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController);
}
