package gregochr.services.config;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigurationViewService {

    private static final Set<String> VALID_MEDIA_TYPES = Set.of("movie", "episode", "clip");
    private static final Set<String> VALID_SCENE_KEYS = Set.of("hue.start-scene", "hue.stop-scene");
    private static final Set<String> VALID_PROPERTY_KEYS = Set.of("sse.heartbeat-interval");

    private final PropertiesRepository repository;
    private final ConfigurationViewFormatter formatter;

    public ConfigurationViewService(PropertiesRepository repository, ConfigurationViewFormatter formatter) {
        this.repository = repository;
        this.formatter = formatter;
    }

    public List<ConfigurationViewSection> getConfigurationSections() {
        return formatter.buildSections(repository.readAllEntries());
    }

    public Map<String, Boolean> getPlexProperties(String mediaType) {
        String prefix = "plex." + mediaType + "-";
        return repository.readBooleanProperties(prefix);
    }

    public Map<String, String> getSceneProperties() {
        return repository.readSceneProperties();
    }

    public void saveProperties(Map<String, String> updates) throws IOException {
        repository.saveProperties(updates, repository.resolvePropertiesPath());
    }

    public boolean isValidMediaType(String mediaType) {
        return VALID_MEDIA_TYPES.contains(mediaType);
    }

    public boolean isValidSceneKey(String key) {
        return VALID_SCENE_KEYS.contains(key);
    }

    public Set<String> getValidPropertyKeys() {
        return VALID_PROPERTY_KEYS;
    }
}
