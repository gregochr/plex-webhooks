package gregochr.controllers;

import gregochr.services.config.ConfigService;
import gregochr.services.config.ConfigurationViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Dashboard Controller Tests")
public class DashboardControllerTest {

    private ConfigService configService;
    private ConfigurationViewService configurationViewService;
    private Model model;

    @BeforeEach
    public void setUp() {
        configService = mock(ConfigService.class);
        configurationViewService = mock(ConfigurationViewService.class);
        when(configurationViewService.getConfigurationSections()).thenReturn(List.of());
        model = mock(Model.class);
    }

    private DashboardController createController(boolean adminTabEnabled) {
        return new DashboardController(configService, configurationViewService, adminTabEnabled);
    }

    @DisplayName("Dashboard returns the classic view name")
    @Test
    public void testDashboardReturnsClassicView() {
        DashboardController controller = createController(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        String viewName = controller.dashboard(model);

        assertEquals("classic", viewName);
    }

    @DisplayName("Dashboard adds hueIntegrationEnabled to model when true")
    @Test
    public void testHueIntegrationEnabledTrue() {
        DashboardController controller = createController(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.dashboard(model);

        verify(model).addAttribute("hueIntegrationEnabled", true);
    }

    @DisplayName("Dashboard adds hueIntegrationEnabled to model when false")
    @Test
    public void testHueIntegrationEnabledFalse() {
        DashboardController controller = createController(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        controller.dashboard(model);

        verify(model).addAttribute("hueIntegrationEnabled", false);
    }

    @DisplayName("Dashboard adds adminTabEnabled true to model")
    @Test
    public void testAdminTabEnabledTrue() {
        DashboardController controller = createController(true);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.dashboard(model);

        verify(model).addAttribute("adminTabEnabled", true);
    }

    @DisplayName("Dashboard adds adminTabEnabled false to model")
    @Test
    public void testAdminTabEnabledFalse() {
        DashboardController controller = createController(false);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        controller.dashboard(model);

        verify(model).addAttribute("adminTabEnabled", false);
    }
}
