package gregochr.services.config;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.controllers.hue.HueSceneController;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link ClientConfig} from the live, hot-reloaded state (not the properties file), so a snapshot taken
 * right after a save already shows the new values.
 */
@Service
public class ClientConfigService {
    private final ConfigService configService;
    private final HueSceneController hueSceneController;
    private final PlexCommandConfig plexCommandConfig;

    public ClientConfigService(ConfigService configService, HueSceneController hueSceneController,
            PlexCommandConfig plexCommandConfig) {
        this.configService = configService;
        this.hueSceneController = hueSceneController;
        this.plexCommandConfig = plexCommandConfig;
    }

    public ClientConfig snapshot() {
        Map<String, Map<String, Boolean>> plexCommands = new LinkedHashMap<>();
        for (PlexMediaType mediaType : PlexMediaType.values()) {
            plexCommands.put(mediaType.getType(), commands(mediaType, plexCommandConfig.getConfig(mediaType)));
        }
        return new ClientConfig(configService.isHueIntegrationEnabled(),
                new ClientConfig.Scenes(hueSceneController.getStartScene(), hueSceneController.getStopScene()),
                plexCommands);
    }

    private static Map<String, Boolean> commands(PlexMediaType mediaType, PlexMediaTypeConfig config) {
        String prefix = "plex." + mediaType.getType() + "-";
        Map<String, Boolean> commands = new LinkedHashMap<>();
        commands.put(prefix + "play-enabled", config.playEnabled());
        commands.put(prefix + "resume-enabled", config.resumeEnabled());
        commands.put(prefix + "stop-enabled", config.stopEnabled());
        commands.put(prefix + "pause-enabled", config.pauseEnabled());
        commands.put(prefix + "scrobble-enabled", config.scrobbleEnabled());
        return commands;
    }
}
