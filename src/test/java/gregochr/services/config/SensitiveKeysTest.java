package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SensitiveKeys Tests")
public class SensitiveKeysTest {

    @DisplayName("Credential keys are sensitive")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "hue.application-key",
            "app.admin.password",
            "HUE.APPLICATION-KEY",
            "dirigera.access-token",
            "service.client-secret",
            "service.api-key",
            "service.apikey",
            "service.api_key",
            "service.key",
            "key",
            "db.PASSWORD",
            "db.password-hash"
    })
    public void testCredentialKeysAreSensitive(String key) {
        assertTrue(SensitiveKeys.isSensitive(key));
    }

    @DisplayName("Non-credential keys are not sensitive")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "hue.hub-id",
            "hue.hub-address",
            "hue.start-scene",
            "hue.stop-scene",
            "hue.status-interval",
            "dirigera.hub-address",
            "dirigera.status-interval",
            "sse.heartbeat-interval",
            "postcode",
            "debug",
            "dashboard.admin-tab-enabled",
            "app.admin.username",
            "plex.movie-play-enabled",
            "monkey.count",
            "zoo.monkey",
            "hub-keyboard",
            "keyboard.layout",
            "hue.keystone-enabled"
    })
    public void testNonCredentialKeysAreNotSensitive(String key) {
        assertFalse(SensitiveKeys.isSensitive(key));
    }

    @DisplayName("Null key is not sensitive")
    @Test
    public void testNullKey() {
        assertFalse(SensitiveKeys.isSensitive(null));
    }
}
