package gregochr.commands;

import gregochr.clients.LocationClient;
import gregochr.clients.SunriseSunsetClient;
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
    private final PlexMediaType mediaType;

    public PlexCommand(PlexEvent plexEvent, String title, int duration, PlexMediaType mediaType) {
        this.plexEvent = plexEvent;
        this.event = plexEvent.getEvent();
        this.title = title;
        this.duration = duration;
        this.mediaType = mediaType;
    }

    public PlexCommand(String unknownEvent, PlexMediaType mediaType) {
        this.plexEvent = null;
        this.event = unknownEvent;
        this.title = "";
        this.duration = 0;
        this.mediaType = mediaType;
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

    public PlexMediaType getMediaType() {
        return mediaType;
    }

    @Override
    public String toString() {
        return "[ Event: %s, Title: %s, Duration: %d, Type: %s ]".formatted(
                this.event, this.title, this.duration,
                this.mediaType != null ? this.mediaType.getType() : "unknown");
    }

    public boolean execute(HueSceneController hueSceneController, LocationClient locationClient, SunriseSunsetClient sunriseSunsetClient, ConfigService configService, PlexCommandConfig plexCommandConfig) {
        if (!configService.isHueIntegrationEnabled()) {
            LOGGER.info("[ Hue integration disabled, skipping ]");
            return false;
        }

        if (plexEvent == null) {
            LOGGER.debug("[ Ignoring unrecognized Plex event: {} ]", this);
            return false;
        }

        if (!plexCommandConfig.isCommandEnabled(plexEvent, mediaType)) {
            LOGGER.info("[ Plex {} command disabled for type {}, skipping ]", plexEvent.name(), mediaType);
            return false;
        }

        LOGGER.info("[ Executing Plex {} Command: {} ]", plexEvent.name(), this);
        return plexEvent.execute(hueSceneController);
    }
}