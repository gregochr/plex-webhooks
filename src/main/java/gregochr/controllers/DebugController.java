package gregochr.controllers;

import gregochr.commands.*;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
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

        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering {} command with type: {} ]", command, type);

        PlexCommand plexCommand = createCommand(command, type);
        boolean result = plexCommand.execute(hueSceneController, locationController, sunriseSunsetController, configService);

        return Map.of("command", command, "type", type, "success", result);
    }

    private PlexCommand createCommand(String command, String type) {
        return switch (command) {
            case "play" -> new PlexPlayCommand("Debug Test", 0, type);
            case "stop" -> new PlexStopCommand("Debug Test", 0, type);
            case "pause" -> new PlexPauseCommand("Debug Test", 0, type);
            case "resume" -> new PlexResumeCommand("Debug Test", 0, type);
            case "scrobble" -> new PlexScrobbleCommand("Debug Test", 0, type);
            default -> new PlexNoOpCommand(command, type);
        };
    }
}
