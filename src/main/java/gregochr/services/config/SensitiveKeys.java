package gregochr.services.config;

import java.util.Locale;
import java.util.Set;

/**
 * Single definition of which configuration keys hold credentials. Values of these keys must never be
 * placed in a view model, HTTP response or SSE payload.
 */
public final class SensitiveKeys {

    private static final Set<String> EXPLICIT_KEYS = Set.of("hue.application-key", "app.admin.password");
    private static final Set<String> SEGMENT_FRAGMENTS = Set.of("password", "secret", "token");

    private SensitiveKeys() {
    }

    /**
     * Returns true when the key names a credential. The last dotted segment is matched
     * case-insensitively: it contains password, secret or token, equals "key", ends with "-key" or "_key",
     * or ends with "apikey". A bare suffix such as "monkey" is deliberately not matched.
     *
     * @param key the property key
     * @return true if the value of the key must never leave the server
     */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (EXPLICIT_KEYS.contains(lower)) {
            return true;
        }
        String segment = lower.substring(lower.lastIndexOf('.') + 1);
        for (String fragment : SEGMENT_FRAGMENTS) {
            if (segment.contains(fragment)) {
                return true;
            }
        }
        return segment.equals("key") || segment.endsWith("-key") || segment.endsWith("_key")
                || segment.endsWith("apikey");
    }
}
