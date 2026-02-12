package gregochr.controllers.dirigera;

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

@DisplayName("DIRIGERA Status Controller Tests")
public class DirigeraStatusControllerTest {

    private DirigeraStatusController controller;
    private SseService sseService;

    @BeforeEach
    public void setUp() throws Exception {
        sseService = mock(SseService.class);
        controller = new DirigeraStatusController();

        Field sseField = DirigeraStatusController.class.getDeclaredField("sseService");
        sseField.setAccessible(true);
        sseField.set(controller, sseService);
    }

    private void setHubAddress(String address) throws Exception {
        Field field = DirigeraStatusController.class.getDeclaredField("hubAddress");
        field.setAccessible(true);
        field.set(controller, address);
    }

    @DisplayName("getStatus returns hubAddress field")
    @Test
    public void testGetStatusReturnsHubAddress() throws Exception {
        setHubAddress("192.168.4.34");

        Map<String, Object> status = controller.getStatus();

        assertEquals("192.168.4.34", status.get("hubAddress"));
    }

    @DisplayName("getStatus returns all expected fields")
    @Test
    public void testGetStatusReturnsAllFields() throws Exception {
        setHubAddress("192.168.4.34");

        Map<String, Object> status = controller.getStatus();

        assertTrue(status.containsKey("hubAddress"));
        assertTrue(status.containsKey("reachable"));
        assertTrue(status.containsKey("timestamp"));
        assertEquals(3, status.size());
    }

    @DisplayName("getStatus returns reachable false when hub is unreachable")
    @Test
    public void testGetStatusUnreachable() throws Exception {
        setHubAddress("192.168.255.254");

        Map<String, Object> status = controller.getStatus();

        assertFalse((Boolean) status.get("reachable"));
    }

    @DisplayName("getStatus returns reachable false for invalid address")
    @Test
    public void testGetStatusInvalidAddress() throws Exception {
        setHubAddress("not.a.real.host");

        Map<String, Object> status = controller.getStatus();

        assertFalse((Boolean) status.get("reachable"));
    }

    @DisplayName("broadcastStatus broadcasts when clients are connected")
    @Test
    public void testBroadcastStatusWithClients() throws Exception {
        setHubAddress("192.168.255.254");
        when(sseService.getClientCount()).thenReturn(1);

        controller.broadcastStatus();

        verify(sseService).broadcastDirigeraStatus(any());
    }

    @DisplayName("broadcastStatus skips broadcast when no clients are connected")
    @Test
    public void testBroadcastStatusNoClients() throws Exception {
        setHubAddress("192.168.255.254");
        when(sseService.getClientCount()).thenReturn(0);

        controller.broadcastStatus();

        verify(sseService, never()).broadcastDirigeraStatus(any());
    }
}
