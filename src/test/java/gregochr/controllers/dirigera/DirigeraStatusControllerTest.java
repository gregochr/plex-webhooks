package gregochr.controllers.dirigera;

import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@DisplayName("DIRIGERA Status Controller Tests")
public class DirigeraStatusControllerTest {

    private DirigeraStatusController controller;
    private SseService sseService;

    @BeforeEach
    public void setUp() {
        sseService = mock(SseService.class);
        controller = spy(new DirigeraStatusController("192.168.255.254", sseService, 30000));
        doReturn(false).when(controller).testConnection();
    }

    @AfterEach
    public void tearDown() {
        controller.destroy();
    }

    @DisplayName("getStatus returns hubAddress field")
    @Test
    public void testGetStatusReturnsHubAddress() {
        Map<String, Object> status = controller.getStatus();

        assertEquals("192.168.255.254", status.get("hubAddress"));
    }

    @DisplayName("getStatus returns all expected fields")
    @Test
    public void testGetStatusReturnsAllFields() {
        Map<String, Object> status = controller.getStatus();

        assertTrue(status.containsKey("hubAddress"));
        assertTrue(status.containsKey("reachable"));
        assertTrue(status.containsKey("timestamp"));
        assertEquals(3, status.size());
    }

    @DisplayName("getStatus returns reachable false when hub is unreachable")
    @Test
    public void testGetStatusUnreachable() {
        Map<String, Object> status = controller.getStatus();

        assertFalse((Boolean) status.get("reachable"));
    }

    @DisplayName("getStatus returns reachable false for invalid address")
    @Test
    public void testGetStatusInvalidAddress() {
        DirigeraStatusController invalidController = spy(new DirigeraStatusController("not.a.real.host", sseService, 30000));
        doReturn(false).when(invalidController).testConnection();

        Map<String, Object> status = invalidController.getStatus();

        assertFalse((Boolean) status.get("reachable"));
        invalidController.destroy();
    }

    @DisplayName("broadcastStatus broadcasts when clients are connected")
    @Test
    public void testBroadcastStatusWithClients() {
        when(sseService.getClientCount()).thenReturn(1);

        controller.broadcastStatus();

        verify(sseService).broadcastDirigeraStatus(any());
    }

    @DisplayName("broadcastStatus skips broadcast when no clients are connected")
    @Test
    public void testBroadcastStatusNoClients() {
        when(sseService.getClientCount()).thenReturn(0);

        controller.broadcastStatus();

        verify(sseService, never()).broadcastDirigeraStatus(any());
    }

    @DisplayName("onPropertiesUpdated reschedules with new interval")
    @Test
    public void testOnPropertiesUpdatedReschedules() {
        controller.init();

        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "dirigera.status-interval", "60000"
        ));

        controller.onPropertiesUpdated(event);

        // Verify no exception and broadcastStatus still works
        when(sseService.getClientCount()).thenReturn(0);
        controller.broadcastStatus();
    }

    @DisplayName("onPropertiesUpdated ignores unrelated keys")
    @Test
    public void testOnPropertiesUpdatedIgnoresUnrelatedKeys() {
        controller.init();

        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "sse.heartbeat-interval", "60000"
        ));

        controller.onPropertiesUpdated(event);
    }
}
