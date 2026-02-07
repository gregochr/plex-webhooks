package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

public class PlexPauseCommand  extends PlexCommand  {
    private static final String EVENT = "media.pause";

    public PlexPauseCommand(String title, int duration, String type) {
        super(title, duration, type);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    protected boolean doExecute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexPauseCommand.class).info("[ Executing Plex Pause Command: {} ]", this);
        return hueSceneController.stopMovieLightScene();
    }
}
