package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

public class PlexStopCommand extends PlexCommand  {
    private static final String EVENT = "media.stop";

    public PlexStopCommand(String title, int duration, String type) {
        super(title, duration, type);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    protected boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexStopCommand.class).info("[ Executing Plex Stop Command: {} ]", this);
        return hueSceneController.stopMovieLightScene();
    }
}
