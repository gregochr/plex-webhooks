package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

public class PlexStopCommand extends PlexCommand  {
    public PlexStopCommand(String event, String title, int duration) {
        super(event, title, duration);
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexStopCommand.class).info("[ Executing Plex Stop Command: " + this.toString() + " ]");

        return hueSceneController.stopMovieLightScene();
    }
}
