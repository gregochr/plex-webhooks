package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Hue Config Tests")
public class HueConfigTest {

    @DisplayName("The bridge client bean is created for the configured hub id and application key")
    @Test
    public void bridgeClientUsesConfiguredHubAndKey() {
        HueBridgeClient client = new HueConfig().hueBridgeClient("001788fffe7107c8", "test-application-key");

        assertEquals("001788fffe7107c8", client.getHubId());
        assertEquals("test-application-key", client.getAppKey());
    }
}
