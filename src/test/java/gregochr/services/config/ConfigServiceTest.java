package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Config Service Tests")
public class ConfigServiceTest {

    private ConfigService configService;

    @BeforeEach
    public void setUp() {
        configService = new ConfigService();
    }

    @DisplayName("Hue integration is enabled by default")
    @Test
    public void testHueIntegrationEnabledByDefault() {
        assertTrue(configService.isHueIntegrationEnabled());
    }

    @DisplayName("Hue integration can be disabled")
    @Test
    public void testHueIntegrationCanBeDisabled() {
        configService.setHueIntegrationEnabled(false);

        assertFalse(configService.isHueIntegrationEnabled());
    }

    @DisplayName("Hue integration can be re-enabled")
    @Test
    public void testHueIntegrationCanBeReEnabled() {
        configService.setHueIntegrationEnabled(false);
        configService.setHueIntegrationEnabled(true);

        assertTrue(configService.isHueIntegrationEnabled());
    }
}
