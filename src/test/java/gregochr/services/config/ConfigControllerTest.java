package gregochr.services.config;

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

    @DisplayName("Get config returns hue integration status")
    @Test
    public void testGetConfigReturnsHueStatus() {
        ConfigService mockService = mock(ConfigService.class);
        when(mockService.isHueIntegrationEnabled()).thenReturn(true);

        ConfigController controller = new ConfigController(mockService);
        Map<String, Object> result = controller.getConfig();

        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
        verify(mockService).isHueIntegrationEnabled();
    }

    @DisplayName("Set hue enabled to true updates service")
    @Test
    public void testSetHueEnabledTrue() {
        ConfigService mockService = mock(ConfigService.class);
        when(mockService.isHueIntegrationEnabled()).thenReturn(true);

        ConfigController controller = new ConfigController(mockService);
        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", true));

        verify(mockService).setHueIntegrationEnabled(true);
        assertTrue((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled to false updates service")
    @Test
    public void testSetHueEnabledFalse() {
        ConfigService mockService = mock(ConfigService.class);
        when(mockService.isHueIntegrationEnabled()).thenReturn(false);

        ConfigController controller = new ConfigController(mockService);
        Map<String, Object> result = controller.setHueEnabled(Map.of("enabled", false));

        verify(mockService).setHueIntegrationEnabled(false);
        assertFalse((Boolean) result.get("hueIntegrationEnabled"));
    }

    @DisplayName("Set hue enabled defaults to true when key missing")
    @Test
    public void testSetHueEnabledDefaultsToTrue() {
        ConfigService mockService = mock(ConfigService.class);
        when(mockService.isHueIntegrationEnabled()).thenReturn(true);

        ConfigController controller = new ConfigController(mockService);
        controller.setHueEnabled(Map.of());

        verify(mockService).setHueIntegrationEnabled(true);
    }
}
