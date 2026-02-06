package gregochr.services.sse;

import jakarta.servlet.http.HttpServletRequest;
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
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        SseEmitter expectedEmitter = new SseEmitter();
        String clientId = "test-client-1";
        String userAgent = "Mozilla/5.0 Test Browser";
        String ipAddress = "192.168.1.100";

        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(mockService.subscribe(clientId, userAgent, ipAddress)).thenReturn(expectedEmitter);

        SseController controller = new SseController(mockService);
        SseEmitter result = controller.subscribe(clientId, userAgent, mockRequest);

        assertNotNull(result);
        verify(mockService).subscribe(clientId, userAgent, ipAddress);
    }

    @DisplayName("Subscribe uses X-Forwarded-For header when present")
    @Test
    public void testSubscribeUsesForwardedIp() {
        SseService mockService = mock(SseService.class);
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        SseEmitter expectedEmitter = new SseEmitter();
        String clientId = "test-client-1";
        String userAgent = "Mozilla/5.0 Test Browser";
        String forwardedIp = "10.0.0.50";

        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(forwardedIp + ", 192.168.1.1");
        when(mockService.subscribe(clientId, userAgent, forwardedIp)).thenReturn(expectedEmitter);

        SseController controller = new SseController(mockService);
        SseEmitter result = controller.subscribe(clientId, userAgent, mockRequest);

        assertNotNull(result);
        verify(mockService).subscribe(clientId, userAgent, forwardedIp);
    }
}
