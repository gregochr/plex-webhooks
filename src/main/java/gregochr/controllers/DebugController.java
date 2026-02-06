package gregochr.controllers;

import gregochr.commands.*;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
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

    @PostMapping("/play")
    public Map<String, Object> triggerPlay(@RequestParam(defaultValue = "movie") String type) {
        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering Play command with type: {} ]", type);
        PlexCommand command = new PlexPlayCommand("Debug Test", 0, type);
        boolean result = command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        return Map.of("command", "play", "type", type, "success", result);
    }

    @PostMapping("/stop")
    public Map<String, Object> triggerStop(@RequestParam(defaultValue = "movie") String type) {
        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering Stop command with type: {} ]", type);
        PlexCommand command = new PlexStopCommand("Debug Test", 0, type);
        boolean result = command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        return Map.of("command", "stop", "type", type, "success", result);
    }

    @PostMapping("/pause")
    public Map<String, Object> triggerPause(@RequestParam(defaultValue = "movie") String type) {
        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering Pause command with type: {} ]", type);
        PlexCommand command = new PlexPauseCommand("Debug Test", 0, type);
        boolean result = command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        return Map.of("command", "pause", "type", type, "success", result);
    }

    @PostMapping("/resume")
    public Map<String, Object> triggerResume(@RequestParam(defaultValue = "movie") String type) {
        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering Resume command with type: {} ]", type);
        PlexCommand command = new PlexResumeCommand("Debug Test", 0, type);
        boolean result = command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        return Map.of("command", "resume", "type", type, "success", result);
    }

    @PostMapping("/scrobble")
    public Map<String, Object> triggerScrobble(@RequestParam(defaultValue = "movie") String type) {
        LogManager.getLogger(DebugController.class).info("[ Debug: Triggering Scrobble command with type: {} ]", type);
        PlexCommand command = new PlexScrobbleCommand("Debug Test", 0, type);
        boolean result = command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        return Map.of("command", "scrobble", "type", type, "success", result);
    }
}
