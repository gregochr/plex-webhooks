package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

public class PlexStopCommand extends PlexCommand  {
    private static final String EVENT = "media.stop";

    public PlexStopCommand(String title, int duration) {
        super(title, duration);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexStopCommand.class).info("[ Executing Plex Stop Command: " + this.toString() + " ]");

        return hueSceneController.stopMovieLightScene();
    }
}
