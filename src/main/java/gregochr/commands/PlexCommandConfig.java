package gregochr.commands;

import gregochr.services.config.BooleanProperties;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlexCommandConfig {
    private final Map<PlexMediaType, PlexMediaTypeConfig> configs;

    public PlexCommandConfig(Map<PlexMediaType, PlexMediaTypeConfig> configs) {
        this.configs = new ConcurrentHashMap<>(configs);
    }

    public boolean isCommandEnabled(PlexEvent event, PlexMediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        PlexMediaTypeConfig config = configs.get(mediaType);
        return config != null && config.isEventEnabled(event);
    }

    /** The current rules for a media type; all off if none are configured. */
    public PlexMediaTypeConfig getConfig(PlexMediaType mediaType) {
        return configs.getOrDefault(mediaType, new PlexMediaTypeConfig(false, false, false, false, false));
    }

    public void updateMediaType(PlexMediaType mediaType, PlexMediaTypeConfig config) {
        configs.put(mediaType, config);
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        Map<String, String> updates = event.getUpdatedProperties();
        for (PlexMediaType mediaType : PlexMediaType.values()) {
            String prefix = "plex." + mediaType.getType() + "-";
            boolean hasUpdates = updates.keySet().stream().anyMatch(k -> k.startsWith(prefix));
            if (hasUpdates) {
                PlexMediaTypeConfig current = configs.getOrDefault(mediaType,
                        new PlexMediaTypeConfig(false, false, false, false, false));
                configs.put(mediaType, new PlexMediaTypeConfig(
                        BooleanProperties.parse(updates.getOrDefault(
                                prefix + "play-enabled", String.valueOf(current.playEnabled()))),
                        BooleanProperties.parse(updates.getOrDefault(
                                prefix + "resume-enabled", String.valueOf(current.resumeEnabled()))),
                        BooleanProperties.parse(updates.getOrDefault(
                                prefix + "stop-enabled", String.valueOf(current.stopEnabled()))),
                        BooleanProperties.parse(updates.getOrDefault(
                                prefix + "pause-enabled", String.valueOf(current.pauseEnabled()))),
                        BooleanProperties.parse(updates.getOrDefault(
                                prefix + "scrobble-enabled", String.valueOf(current.scrobbleEnabled())))
                ));
            }
        }
    }
}
