package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

public class PlexPlayCommand extends PlexCommand  {
    private static final String EVENT = "media.play";

    public PlexPlayCommand(String title, int duration) {
        super(title, duration);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        LogManager.getLogger(PlexPlayCommand.class).info("[ Executing Plex Play Command: " + this.toString() + " ]");

        return hueSceneController.startMovieLightScene();
    }
}
