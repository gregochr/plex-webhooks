package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlexNoOpCommand extends PlexCommand {
    private static final Logger LOGGER = LogManager.getLogger(PlexNoOpCommand.class);
    private final String unknownEvent;

    public PlexNoOpCommand(String unknownEvent, String unknownType) {
        super("", 0, "");
        this.unknownEvent = unknownEvent;
        this.type = unknownType;
    }

    @Override
    public String getEvent() {
        return unknownEvent;
    }

    @Override
    protected boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LOGGER.debug("[ Ignoring unrecognized Plex event: {} ]", this);
        return false;
    }
}
