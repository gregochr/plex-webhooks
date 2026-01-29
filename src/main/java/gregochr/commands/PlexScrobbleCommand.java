package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.controllers.hue.HueSceneController;

public class PlexScrobbleCommand extends PlexCommand  {
    public PlexScrobbleCommand(String event, String title, int duration) {
        super(event, title, duration);
    }

    public void execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        hueSceneController.stopMovieLightScene();
    }
}
