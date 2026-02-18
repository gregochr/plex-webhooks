package gregochr.controllers;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
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
    private final ConfigService configService;
    private final PlexCommandConfig plexCommandConfig;

    public DebugController(HueSceneController hueSceneController,
                           ConfigService configService,
                           PlexCommandConfig plexCommandConfig) {
        this.hueSceneController = hueSceneController;
        this.configService = configService;
        this.plexCommandConfig = plexCommandConfig;
    }

    @PostMapping("/{command}")
    public Map<String, Object> triggerCommand(
            @PathVariable String command,
            @RequestParam(defaultValue = "movie") String type) {

        PlexMediaType mediaType = PlexMediaType.fromType(type).orElse(null);
        PlexCommand plexCommand = createCommand(command, mediaType);
        LOGGER.info("[ Debug: Triggering {} command with type: {} ]", plexCommand, type);

        boolean result = plexCommand.execute(hueSceneController, configService, plexCommandConfig);

        return Map.of("command", command, "type", type, "success", result);
    }

    private PlexCommand createCommand(String command, PlexMediaType mediaType) {
        return switch (command) {
            case "play" -> new PlexCommand(PlexEvent.PLAY, "Debug Play Command Test", 0, mediaType);
            case "stop" -> new PlexCommand(PlexEvent.STOP, "Debug Stop Command Test", 0, mediaType);
            case "pause" -> new PlexCommand(PlexEvent.PAUSE, "Debug Pause Test", 0, mediaType);
            case "resume" -> new PlexCommand(PlexEvent.RESUME, "Debug Resume Test", 0, mediaType);
            case "scrobble" -> new PlexCommand(PlexEvent.SCROBBLE, "Debug Scrobble Test", 0, mediaType);
            default -> new PlexCommand(command, mediaType);
        };
    }
}
