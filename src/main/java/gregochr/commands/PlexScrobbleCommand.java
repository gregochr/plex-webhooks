package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;

public class PlexScrobbleCommand extends PlexCommand  {
    private static final String EVENT = "media.scrobble";

    public PlexScrobbleCommand(String title, int duration) {
        super(title, duration);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController) {
        return hueSceneController.stopMovieLightScene();
    }
}
