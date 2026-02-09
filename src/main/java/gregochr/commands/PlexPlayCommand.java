package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlexPlayCommand extends PlexCommand  {
    private static final Logger LOGGER = LogManager.getLogger(PlexPlayCommand.class);
    private static final String EVENT = "media.play";

    public PlexPlayCommand(String title, int duration, String type) {
        super(title, duration, type);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    protected boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LOGGER.info("[ Executing Plex Play Command: {} ]", this);
        return hueSceneController.startMovieLightScene();
    }
}
