package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;

public abstract class PlexCommand {
    protected String title;
    protected int duration;
    protected String type;

    public PlexCommand(String title, int duration, String type) {
        this.title = title;
        this.duration = duration;
        this.type = type;
    }

    public abstract String getEvent();

    public String getTitle() {
        return this.title;
    }
    public int getDuration() { return this.duration; }
    public String getType() { return this.type; }

    @Override
    public String toString() {
        return "[ Event: " + this.getEvent() + ", " +
                "Title: " + this.title + ", " +
                "Duration: " + this.duration +
                ", Type: " + this.type + " ]";
    }
    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController, ConfigService configService) {
        if (!configService.isHueIntegrationEnabled()) {
            LogManager.getLogger(getClass()).info("[ Hue integration disabled, skipping ]");
            return false;
        }

        if (!this.type.equals("movie")) {
            LogManager.getLogger(getClass()).info("[ PlexCommand ignores media type {$1}, skipping ]", this.type);
            return false;
        }
        return doExecute(hueSceneController, locationController, sunriseSunsetController);
    }

    protected abstract boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController);
}