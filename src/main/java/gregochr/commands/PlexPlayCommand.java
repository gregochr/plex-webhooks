package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.controllers.hue.HueSceneController;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;

public class PlexPlayCommand extends PlexCommand  {
    public PlexPlayCommand(String event, String title) {
        super(event, title);
    }

    public void execute(HueSceneController controller) {
        LocationController locationController = new LocationController();
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();

        LocationRootBean locationRootBean = null;
        SunriseSunsetRootBean sunriseSunsetRootBean = null;

        LogManager.getLogger(PlexPlayCommand.class).info("[ Executing Plex Play Command: " + this.toString() + " ]");

        try {
             locationRootBean = locationController.getLatitudeAndLogitude();
             LogManager.getLogger(PlexPlayCommand.class).info("Location Root Bean: " + locationRootBean.toString());

             sunriseSunsetRootBean = sunriseSunsetController.getSunsetTimeForToday(locationController.getLatitudeAndLogitude().getLocationResultBean().getLatitude(),
                     locationController.getLatitudeAndLogitude().getLocationResultBean().getLongitude());
            LogManager.getLogger(PlexPlayCommand.class).info("Sunrise / Sunset Root Bean: " + sunriseSunsetRootBean.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        controller.startMovieLightScene();
    }
}
