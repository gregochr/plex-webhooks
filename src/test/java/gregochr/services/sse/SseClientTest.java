package gregochr.services.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SSE Client Tests")
public class SseClientTest {

    private static final String TEST_IP = "192.168.1.100";

    @DisplayName("Detects Chrome on macOS")
    @Test
    public void testChromeMacOS() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                TEST_IP, Instant.now());

        assertEquals("Chrome on macOS", client.getBrowserInfo());
    }

    @DisplayName("Detects Safari on iOS")
    @Test
    public void testSafariIOS() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                TEST_IP, Instant.now());

        assertEquals("Safari on macOS", client.getBrowserInfo());
    }

    @DisplayName("Detects Safari on iPad")
    @Test
    public void testSafariIPad() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                TEST_IP, Instant.now());

        assertEquals("Safari on macOS", client.getBrowserInfo());
    }

    @DisplayName("Detects Edge on Windows")
    @Test
    public void testEdgeWindows() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
                TEST_IP, Instant.now());

        assertEquals("Edge on Windows", client.getBrowserInfo());
    }

    @DisplayName("Detects Firefox on Linux")
    @Test
    public void testFirefoxLinux() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0",
                TEST_IP, Instant.now());

        assertEquals("Firefox on Linux", client.getBrowserInfo());
    }

    @DisplayName("Returns Unknown for empty user agent")
    @Test
    public void testEmptyUserAgent() {
        SseClient client = new SseClient("test", null, "", TEST_IP, Instant.now());

        assertEquals("Unknown", client.getBrowserInfo());
    }

    @DisplayName("Returns Unknown for null user agent")
    @Test
    public void testNullUserAgent() {
        SseClient client = new SseClient("test", null, null, TEST_IP, Instant.now());

        assertEquals("Unknown", client.getBrowserInfo());
    }
}
