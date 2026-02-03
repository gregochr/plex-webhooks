package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;

public class PlexPauseCommand  extends PlexCommand  {
    private static final String EVENT = "media.pause";

    public PlexPauseCommand(String title, int duration) {
        super(title, duration);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        return hueSceneController.stopMovieLightScene();
    }
}
