package gregochr.commands.integration;

import gregochr.hue.client.HueBridgeClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Base class for Plex command integration tests.
 *
 * Mocks the HueBridgeClient so these tests verify command-routing logic
 * (is the right event dispatched for the right media type?) without
 * requiring a live Philips Hue bridge.
 */
public abstract class PlexCommandIntegrationTestBase {

    @MockBean
    protected HueBridgeClient hueBridgeClient;

    @BeforeEach
    void stubHueBridge() {
        when(hueBridgeClient.activateScene(anyString())).thenReturn(true);
    }
}
