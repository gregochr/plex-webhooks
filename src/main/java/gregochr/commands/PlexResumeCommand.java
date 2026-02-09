package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlexResumeCommand extends PlexCommand  {
    private static final Logger LOGGER = LogManager.getLogger(PlexResumeCommand.class);
    private static final String EVENT = "media.resume";

    public PlexResumeCommand(String title, int duration, String type) {
        super(title, duration, type);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    protected boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LOGGER.info("[ Executing Plex Resume Command: {} ]", this);
        return hueSceneController.startMovieLightScene();
    }
}
