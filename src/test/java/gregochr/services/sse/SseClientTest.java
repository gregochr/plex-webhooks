package gregochr.services.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static gregochr.support.TestClocks.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SSE Client Tests")
public class SseClientTest {

    private static final String TEST_IP = "192.168.1.100";

    @DisplayName("Detects Chrome on macOS")
    @Test
    public void testChromeMacOS() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Chrome on macOS", client.getBrowserInfo());
    }

    @DisplayName("A real iPhone Safari user agent is reported as macOS, because it contains 'like Mac OS X'")
    @Test
    public void testSafariIOS() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
                + " (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Safari on macOS", client.getBrowserInfo());
    }

    @DisplayName("A real iPad Safari user agent is reported as macOS, because it contains 'like Mac OS X'")
    @Test
    public void testSafariIPad() {
        String ua = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
                + " (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Safari on macOS", client.getBrowserInfo());
    }

    @DisplayName("Detects Edge on Windows")
    @Test
    public void testEdgeWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Edge on Windows", client.getBrowserInfo());
    }

    @DisplayName("Detects Firefox on Linux")
    @Test
    public void testFirefoxLinux() {
        SseClient client = new SseClient("test", null,
                "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0",
                TEST_IP, INSTANT);

        assertEquals("Firefox on Linux", client.getBrowserInfo());
    }

    @DisplayName("Detects iOS from an iPhone user agent that lacks 'Mac OS X'")
    @Test
    public void testIPhoneWithoutMacOsX() {
        SseClient client = new SseClient("test", null, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Safari/604.1",
                TEST_IP, INSTANT);

        assertEquals("Safari on iOS", client.getBrowserInfo());
    }

    @DisplayName("Detects iOS from an iPad user agent that lacks 'Mac OS X'")
    @Test
    public void testIPadWithoutMacOsX() {
        SseClient client = new SseClient("test", null, "Mozilla/5.0 (iPad; CPU OS 17_0) Safari/604.1",
                TEST_IP, INSTANT);

        assertEquals("Safari on iOS", client.getBrowserInfo());
    }

    @DisplayName("Detects Chrome on Android")
    @Test
    public void testChromeAndroid() {
        String ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Chrome on Android", client.getBrowserInfo());
    }

    @DisplayName("Detects Safari on macOS")
    @Test
    public void testSafariMacOS() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15"
                + " (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
        SseClient client = new SseClient("test", null, ua, TEST_IP, INSTANT);

        assertEquals("Safari on macOS", client.getBrowserInfo());
    }

    @DisplayName("Reports an unrecognised browser and operating system as Unknown on Unknown")
    @Test
    public void testUnrecognisedAgent() {
        SseClient client = new SseClient("test", null, "curl/8.4.0", TEST_IP, INSTANT);

        assertEquals("Unknown on Unknown", client.getBrowserInfo());
    }

    @DisplayName("A Safari/ token alongside a bare 'Chrome' word is not reported as Safari")
    @Test
    public void testSafariTokenWithChromeWord() {
        SseClient client = new SseClient("test", null, "Mozilla/5.0 Chrome Safari/537.36", TEST_IP, INSTANT);

        assertEquals("Unknown on Unknown", client.getBrowserInfo());
    }

    @DisplayName("Returns Unknown for empty user agent")
    @Test
    public void testEmptyUserAgent() {
        SseClient client = new SseClient("test", null, "", TEST_IP, INSTANT);

        assertEquals("Unknown", client.getBrowserInfo());
    }

    @DisplayName("Returns Unknown for null user agent")
    @Test
    public void testNullUserAgent() {
        SseClient client = new SseClient("test", null, null, TEST_IP, INSTANT);

        assertEquals("Unknown", client.getBrowserInfo());
    }
}
