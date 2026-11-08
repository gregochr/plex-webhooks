package gregochr.services.config;

import gregochr.commands.PlexMediaType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ConfigurationViewService {

    private static final Set<String> VALID_MEDIA_TYPES = Arrays.stream(PlexMediaType.values())
            .map(PlexMediaType::getType)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> VALID_SCENE_KEYS = Set.of("hue.start-scene", "hue.stop-scene");
    private static final Set<String> VALID_PROPERTY_KEYS = Set.of("sse.heartbeat-interval", "hue.status-interval",
            "hue.scene-catalog-interval", "dirigera.status-interval");

    public static final long MIN_INTERVAL_MS = 1_000L;
    public static final long MAX_INTERVAL_MS = 86_400_000L;
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("\\d{1,18}");

    private final PropertiesRepository repository;
    private final ConfigurationViewFormatter formatter;
    private final ApplicationEventPublisher eventPublisher;

    public ConfigurationViewService(PropertiesRepository repository, ConfigurationViewFormatter formatter,
                                    ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.formatter = formatter;
        this.eventPublisher = eventPublisher;
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

    /**
     * Writes the updates to the settings file, then publishes {@link PropertiesUpdatedEvent}. Synchronized around
     * both, because a save reads the whole file and rewrites it: two concurrent saves would otherwise each start
     * from the same text, and the second rewrite would lose the first one's change.
     *
     * @throws IllegalArgumentException if a key or a value contains a CR or LF, which would inject a line into
     *     the file (callers check {@link #hasLineBreak} first and answer 400)
     */
    public synchronized void saveProperties(Map<String, String> updates) throws IOException {
        if (hasLineBreak(updates)) {
            throw new IllegalArgumentException("A setting key or value must not contain a line break");
        }
        repository.saveProperties(updates, repository.resolvePropertiesPath());
        eventPublisher.publishEvent(new PropertiesUpdatedEvent(this, updates));
    }

    /** True if any key or value in the map contains a CR or LF (a null value is not a line break). */
    public boolean hasLineBreak(Map<String, String> updates) {
        return updates.entrySet().stream()
                .anyMatch(entry -> hasLineBreak(entry.getKey()) || hasLineBreak(entry.getValue()));
    }

    private static boolean hasLineBreak(String text) {
        return text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }

    /**
     * Checks a value for a whitelisted property key. Interval keys must be a whole number of milliseconds
     * in [{@value #MIN_INTERVAL_MS}, {@value #MAX_INTERVAL_MS}]; other keys have no value constraint.
     */
    public boolean isValidPropertyValue(String key, String value) {
        if (!VALID_PROPERTY_KEYS.contains(key)) {
            return true;
        }
        return parseIntervalMs(value) != null;
    }

    /**
     * Parses an interval in milliseconds, returning null if it is not a whole number in the allowed range.
     */
    public static Long parseIntervalMs(String value) {
        if (value == null || !INTERVAL_PATTERN.matcher(value).matches()) {
            return null;
        }
        long ms = Long.parseLong(value);
        return ms >= MIN_INTERVAL_MS && ms <= MAX_INTERVAL_MS ? ms : null;
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
