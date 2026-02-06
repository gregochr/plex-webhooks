package gregochr.services.config;

import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Config Controller Tests")
public class ConfigControllerTest {

    private ConfigService configService;
    private SseService sseService;
    private ConfigController controller;

    @BeforeEach
    public void setUp() {
        configService = mock(ConfigService.class);
        sseService = mock(SseService.class);
        controller = new ConfigController(configService, sseService);
    }

    @DisplayName("Get config returns hue integration status")
    @Test
    public void testGetConfigReturnsHueStatus() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> result = controller.getConfig();

        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
        verify(configService).isHueIntegrationEnabled();
    }

    @DisplayName("Set hue enabled to true updates service")
    @Test
    public void testSetHueEnabledTrue() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", true));

        verify(configService).setHueIntegrationEnabled(true);
        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled to false updates service")
    @Test
    public void testSetHueEnabledFalse() {
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", false));

        verify(configService).setHueIntegrationEnabled(false);
        assertFalse((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled defaults to true when key missing")
    @Test
    public void testSetHueEnabledDefaultsToTrue() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.setHueEnabled(Map.of());

        verify(configService).setHueIntegrationEnabled(true);
    }

    @DisplayName("Set hue enabled broadcasts config via SSE")
    @Test
    public void testSetHueEnabledBroadcastsConfig() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.setHueEnabled(Map.of("enabled", true));

        verify(sseService).broadcastConfig(Map.of("hueIntegrationEnabled", true));
    }
}
