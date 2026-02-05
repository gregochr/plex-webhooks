package gregochr.commands;

import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
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

    @Override
    public boolean execute(HueSceneController hueSceneController, LocationController locationController, SunriseSunsetController sunriseSunsetController, ConfigService configService) {
        LogManager.getLogger(PlexPlayCommand.class).info("[ Executing Plex Play Command: {} ]", this);

        if (!configService.isHueIntegrationEnabled()) {
            LogManager.getLogger(PlexPlayCommand.class).info("[ Hue integration disabled, skipping ]");
            return false;
        }

        return hueSceneController.startMovieLightScene();
    }
}
