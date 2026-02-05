package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;

public class PlexResumeCommand extends PlexCommand  {
    private static final String EVENT = "media.resume";

    public PlexResumeCommand(String title, int duration) {
        super(title, duration);
    }

    @Override
    public String getEvent() {
        return EVENT;
    }

    @Override
    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController, ConfigService configService) {
        if (!configService.isHueIntegrationEnabled()) {
            LogManager.getLogger(PlexResumeCommand.class).info("[ Hue integration disabled, skipping ]");
            return false;
        }

        return hueSceneController.startMovieLightScene();
    }
}
