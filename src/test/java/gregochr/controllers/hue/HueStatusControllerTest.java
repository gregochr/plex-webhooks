package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@DisplayName("Hue Status Controller Tests")
public class HueStatusControllerTest {

    private HueStatusController controller;
    private ConfigService configService;
    private SseService sseService;

    @BeforeEach
    public void setUp() throws Exception {
        configService = mock(ConfigService.class);
        sseService = mock(SseService.class);
        controller = new HueStatusController();

        // Set protected fields from HueController
        controller.hubAddress = "192.168.1.100";
        controller.hubId = "001788fffe7107c8";
        controller.applicationKey = "TestApplicationKey1234";
        // Set private configService via reflection
        Field configField = HueController.class.getDeclaredField("configService");
        configField.setAccessible(true);
        configField.set(controller, configService);

        // Set private sseService via reflection
        Field sseField = HueStatusController.class.getDeclaredField("sseService");
        sseField.setAccessible(true);
        sseField.set(controller, sseService);
    }

    @DisplayName("getStatus returns all expected fields")
    @Test
    public void testGetStatusReturnsAllFields() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> status = controller.getStatus();

        assertEquals("192.168.1.100", status.get("hubAddress"));
        assertEquals("001788fffe7107c8", status.get("hubId"));
        assertEquals("TestApplicationKey1234", status.get("applicationKey"));
        assertTrue(status.containsKey("connected"));
        assertTrue(status.containsKey("integrationEnabled"));
        assertTrue(status.containsKey("timestamp"));
    }

    @DisplayName("getStatus returns integrationEnabled true when enabled")
    @Test
    public void testGetStatusIntegrationEnabledTrue() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> status = controller.getStatus();

        assertTrue((Boolean) status.get("integrationEnabled"));
    }

    @DisplayName("getStatus returns integrationEnabled false when disabled")
    @Test
    public void testGetStatusIntegrationEnabledFalse() {
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        Map<String, Object> status = controller.getStatus();

        assertFalse((Boolean) status.get("integrationEnabled"));
    }

    @DisplayName("getStatus sends login unmasked")
    @Test
    public void testGetStatusLoginUnmasked() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> status = controller.getStatus();

        assertEquals("TestApplicationKey1234", status.get("applicationKey"));
    }

    @DisplayName("getStatus returns connected false when bridge unreachable")
    @Test
    public void testGetStatusConnectionFails() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);

        Map<String, Object> status = controller.getStatus();

        assertFalse((Boolean) status.get("connected"));
    }

    @DisplayName("testConnectionEndpoint returns hubId")
    @Test
    public void testConnectionEndpointReturnsHubId() {
        Map<String, Object> result = controller.testConnectionEndpoint();

        assertEquals("001788fffe7107c8", result.get("hubId"));
    }

    @DisplayName("testConnectionEndpoint returns connected false when unreachable")
    @Test
    public void testConnectionEndpointReturnsFalse() {
        Map<String, Object> result = controller.testConnectionEndpoint();

        assertFalse((Boolean) result.get("connected"));
    }

    @DisplayName("broadcastStatus broadcasts when clients are connected")
    @Test
    public void testBroadcastStatusWithClients() {
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        when(sseService.getClientCount()).thenReturn(1);

        controller.broadcastStatus();

        verify(sseService).broadcastHueStatus(any());
    }

    @DisplayName("broadcastStatus skips broadcast when no clients are connected")
    @Test
    public void testBroadcastStatusNoClients() {
        when(sseService.getClientCount()).thenReturn(0);

        controller.broadcastStatus();

        verify(sseService, never()).broadcastHueStatus(any());
    }
}
