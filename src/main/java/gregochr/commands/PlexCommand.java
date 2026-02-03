package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;

public abstract class PlexCommand {
    protected String title;
    protected int duration;

    public PlexCommand(String title, int duration) {
        this.title = title;
        this.duration = duration;
    }

    public abstract String getEvent();

    public String getTitle() {
        return this.title;
    }
    public int getDuration() { return this.duration; }

    public String toString() {
        return "[ Event: " + this.getEvent() + ", " +
                "Title: " + this.title + ", " +
                "Duration: " + this.duration + " ]";
    }

    abstract public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController);
}