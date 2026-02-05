package gregochr.services.sse;

import gregochr.commands.PlexPlayCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("SSE Service Tests")
public class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    public void setUp() {
        sseService = new SseService();
    }

    @DisplayName("Subscribe returns a non-null SseEmitter")
    @Test
    public void testSubscribeReturnsEmitter() {
        SseEmitter emitter = sseService.subscribe();

        assertNotNull(emitter);
    }

    @DisplayName("Subscribe increments client count")
    @Test
    public void testSubscribeIncrementsClientCount() {
        assertEquals(0, sseService.getClientCount());

        sseService.subscribe();
        assertEquals(1, sseService.getClientCount());

        sseService.subscribe();
        assertEquals(2, sseService.getClientCount());
    }

    @DisplayName("Broadcast does not throw when no clients connected")
    @Test
    public void testBroadcastWithNoClients() {
        SseEventDto event = new SseEventDto(
                new PlexPlayCommand("Test Movie", 7200000),
                "{\"event\":\"media.play\"}"
        );

        sseService.broadcast(event);

        assertEquals(0, sseService.getClientCount());
    }
}
