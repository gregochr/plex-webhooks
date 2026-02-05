package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;

public class PlexNoOpCommand extends PlexCommand {
    private final String unknownEvent;

    public PlexNoOpCommand(String unknownEvent) {
        super("", 0);
        this.unknownEvent = unknownEvent;
    }

    @Override
    public String getEvent() {
        return unknownEvent;
    }

    @Override
    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController, ConfigService configService) {
        LogManager.getLogger(PlexNoOpCommand.class).debug("[ Ignoring unrecognized Plex event: {} ]", unknownEvent);
        return false;
    }
}
