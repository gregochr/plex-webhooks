package gregochr.commands;

import java.util.Map;

public class PlexCommandConfig {
    private final Map<PlexMediaType, PlexMediaTypeConfig> configs;

    public PlexCommandConfig(Map<PlexMediaType, PlexMediaTypeConfig> configs) {
        this.configs = configs;
    }

    public boolean isCommandEnabled(PlexEvent event, PlexMediaType mediaType) {
        if (mediaType == null) return false;
        PlexMediaTypeConfig config = configs.get(mediaType);
        return config != null && config.isEventEnabled(event);
    }
}
