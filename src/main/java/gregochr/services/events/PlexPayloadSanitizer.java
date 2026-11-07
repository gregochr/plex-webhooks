package gregochr.services.events;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Rebuilds a raw Plex webhook payload from an allowlist, so nothing private reaches the public history or the
 * public SSE stream. A raw payload carries the account name and avatar ({@code Account}), the player's WAN address
 * and uuid, the server uuid and artwork paths; none of those is on the list, so none survives. Only scalar values
 * are copied: a nested object under an allowlisted key is dropped rather than passed through.
 */
@Component
public class PlexPayloadSanitizer {
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private static final List<String> TOP_LEVEL = List.of("event", "user", "owner");
    private static final List<String> PLAYER = List.of("title", "local");
    private static final List<String> METADATA = List.of("type", "title", "grandparentTitle", "parentTitle",
            "parentIndex", "index", "year", "librarySectionTitle", "duration", "viewOffset");

    /**
     * Returns the allowlisted subset of the payload as JSON text.
     *
     * @param rawPayload the Plex payload exactly as received
     * @return a JSON object containing only allowlisted keys; {@code {}} if the payload is not an object
     * @throws tools.jackson.core.JacksonException if the payload is not valid JSON
     */
    public String sanitise(String rawPayload) {
        JsonNode root = OBJECT_MAPPER.readTree(rawPayload);
        ObjectNode clean = OBJECT_MAPPER.createObjectNode();
        copyScalars(root, TOP_LEVEL, clean);
        copySection(root, "Player", PLAYER, clean);
        copySection(root, "Metadata", METADATA, clean);
        return OBJECT_MAPPER.writeValueAsString(clean);
    }

    private static void copySection(JsonNode root, String name, List<String> keys, ObjectNode target) {
        JsonNode section = root.get(name);
        if (section == null || !section.isObject()) {
            return;
        }
        ObjectNode copy = OBJECT_MAPPER.createObjectNode();
        copyScalars(section, keys, copy);
        if (!copy.isEmpty()) {
            target.set(name, copy);
        }
    }

    private static void copyScalars(JsonNode source, List<String> keys, ObjectNode target) {
        for (String key : keys) {
            JsonNode value = source.get(key);
            if (value != null && value.isValueNode() && !value.isNull()) {
                target.set(key, value);
            }
        }
    }
}
