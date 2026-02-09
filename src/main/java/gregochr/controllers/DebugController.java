package gregochr.controllers;

import gregochr.commands.*;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
    private static final Logger LOGGER = LogManager.getLogger(DebugController.class);
    private final HueSceneController hueSceneController;
    private final LocationController locationController;
    private final SunriseSunsetController sunriseSunsetController;
    private final ConfigService configService;

    public DebugController(HueSceneController hueSceneController,
                           LocationController locationController,
                           SunriseSunsetController sunriseSunsetController,
                           ConfigService configService) {
        this.hueSceneController = hueSceneController;
        this.locationController = locationController;
        this.sunriseSunsetController = sunriseSunsetController;
        this.configService = configService;
    }

    @PostMapping("/{command}")
    public Map<String, Object> triggerCommand(
            @PathVariable String command,
            @RequestParam(defaultValue = "movie") String type) {

        PlexCommand plexCommand = createCommand(command, type);
        LOGGER.info("[ Debug: Triggering {} command with type: {} ]", plexCommand, type);

        boolean result = plexCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService);

        return Map.of("command", command, "type", type, "success", result);
    }

    private PlexCommand createCommand(String command, String type) {
        return switch (command) {
            case "play" -> new PlexPlayCommand("Debug Play Command Test", 0, type);
            case "stop" -> new PlexStopCommand("Debug Stop Command Test", 0, type);
            case "pause" -> new PlexPauseCommand("Debug Pause Test", 0, type);
            case "resume" -> new PlexResumeCommand("Debug Resume Test", 0, type);
            case "scrobble" -> new PlexScrobbleCommand("Debug Scrobble Test", 0, type);
            default -> new PlexNoOpCommand(command, type);
        };
    }
}
