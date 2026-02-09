package gregochr.services.sse;

import gregochr.commands.PlexPlayCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("SSE Service Tests")
public class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    public void setUp() {
        sseService = new SseService();
    }

    private static final String TEST_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final String TEST_IP_ADDRESS = "192.168.1.100";

    @DisplayName("Subscribe returns a non-null SseEmitter")
    @Test
    public void testSubscribeReturnsEmitter() {
        SseEmitter emitter = sseService.subscribe("client-1", TEST_USER_AGENT, TEST_IP_ADDRESS);

        assertNotNull(emitter);
    }

    @DisplayName("Subscribe increments client count for different clients")
    @Test
    public void testSubscribeIncrementsClientCount() {
        assertEquals(0, sseService.getClientCount());

        sseService.subscribe("client-1", TEST_USER_AGENT, TEST_IP_ADDRESS);
        assertEquals(1, sseService.getClientCount());

        sseService.subscribe("client-2", TEST_USER_AGENT, TEST_IP_ADDRESS);
        assertEquals(2, sseService.getClientCount());
    }

    @DisplayName("Subscribe replaces existing connection for same client")
    @Test
    public void testSubscribeReplacesExistingClient() {
        assertEquals(0, sseService.getClientCount());

        sseService.subscribe("client-1", TEST_USER_AGENT, TEST_IP_ADDRESS);
        assertEquals(1, sseService.getClientCount());

        // Same client reconnects - should replace, not add
        sseService.subscribe("client-1", TEST_USER_AGENT, TEST_IP_ADDRESS);
        assertEquals(1, sseService.getClientCount());
    }

    @DisplayName("Broadcast does not throw when no clients connected")
    @Test
    public void testBroadcastWithNoClients() {
        SseEventDto event = new SseEventDto(
                new PlexPlayCommand("Test Movie", 7200000, "movie"),
                "{\"event\":\"media.play\"}"
        );

        sseService.broadcast(event);

        assertEquals(0, sseService.getClientCount());
    }

    @DisplayName("BroadcastConfig does not throw when no clients connected")
    @Test
    public void testBroadcastConfigWithNoClients() {
        Map<String, Object> config = Map.of("hueIntegrationEnabled", true);

        sseService.broadcastConfig(config);

        assertEquals(0, sseService.getClientCount());
    }

    @DisplayName("BroadcastHueStatus does not throw when no clients connected")
    @Test
    public void testBroadcastHueStatusWithNoClients() {
        Map<String, Object> status = Map.of("connected", true, "hubAddress", "192.168.4.38");

        sseService.broadcastHueStatus(status);

        assertEquals(0, sseService.getClientCount());
    }

    @DisplayName("BroadcastDirigeraStatus does not throw when no clients connected")
    @Test
    public void testBroadcastDirigeraStatusWithNoClients() {
        Map<String, Object> status = Map.of("reachable", true, "hubAddress", "192.168.4.34");

        sseService.broadcastDirigeraStatus(status);

        assertEquals(0, sseService.getClientCount());
    }

    @DisplayName("Heartbeat does not throw when no clients connected")
    @Test
    public void testHeartbeatWithNoClients() {
        sseService.heartbeat();

        assertEquals(0, sseService.getClientCount());
    }

    @DisplayName("Heartbeat does not throw when clients connected")
    @Test
    public void testHeartbeatWithClients() {
        sseService.subscribe("client-1", TEST_USER_AGENT, TEST_IP_ADDRESS);
        sseService.subscribe("client-2", TEST_USER_AGENT, TEST_IP_ADDRESS);

        sseService.heartbeat();

        // Clients may be removed if send fails, but method should not throw
        // In unit test context, emitters aren't connected to real clients
    }
}
