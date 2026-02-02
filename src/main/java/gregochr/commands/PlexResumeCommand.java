package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.controllers.hue.HueSceneController;

public class PlexResumeCommand extends PlexCommand  {
    public PlexResumeCommand(String event, String  title, int duration) {
        super(event, title, duration);
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        return hueSceneController.startMovieLightScene();
    }
}
