package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;

public class PlexPlayCommand extends PlexCommand  {
    public PlexPlayCommand(String event, String title, int duration) {
        super(event, title, duration);
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexPlayCommand.class).info("[ Executing Plex Play Command: " + this.toString() + " ]");

        return hueSceneController.startMovieLightScene();
    }
}
