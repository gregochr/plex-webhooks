package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlexCommand {
    private static final Logger LOGGER = LogManager.getLogger(PlexCommand.class);

    private final PlexEvent plexEvent;
    private final String event;
    private final String title;
    private final int duration;
    private final String type;

    public PlexCommand(PlexEvent plexEvent, String title, int duration, String type) {
        this.plexEvent = plexEvent;
        this.event = plexEvent.getEvent();
        this.title = title;
        this.duration = duration;
        this.type = type;
    }

    public PlexCommand(String unknownEvent, String type) {
        this.plexEvent = null;
        this.event = unknownEvent;
        this.title = "";
        this.duration = 0;
        this.type = type;
    }

    public String getEvent() {
        return event;
    }

    public PlexEvent getPlexEvent() {
        return plexEvent;
    }

    public String getTitle() {
        return title;
    }

    public int getDuration() {
        return duration;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "[ Event: " + this.event + ", " +
                "Title: " + this.title + ", " +
                "Duration: " + this.duration +
                ", Type: " + this.type + " ]";
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController, ConfigService configService) {
        if (!configService.isHueIntegrationEnabled()) {
            LOGGER.info("[ Hue integration disabled, skipping ]");
            return false;
        }

        if (!this.type.equals("movie")) {
            LOGGER.info("[ PlexCommand ignores media type {}, skipping ]", this.type);
            return false;
        }

        if (plexEvent == null) {
            LOGGER.debug("[ Ignoring unrecognized Plex event: {} ]", this);
            return false;
        }

        LOGGER.info("[ Executing Plex {} Command: {} ]", plexEvent.name(), this);
        return plexEvent.execute(hueSceneController);
    }
}
