package gregochr.services.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SSE Controller Tests")
public class SseControllerTest {

    @DisplayName("Subscribe endpoint returns emitter from service")
    @Test
    public void testSubscribeReturnsEmitter() {
        SseService mockService = mock(SseService.class);
        SseEmitter expectedEmitter = new SseEmitter();
        when(mockService.subscribe()).thenReturn(expectedEmitter);

        SseController controller = new SseController(mockService);
        SseEmitter result = controller.subscribe();

        assertNotNull(result);
        verify(mockService).subscribe();
    }
}
