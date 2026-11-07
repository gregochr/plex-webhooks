package gregochr.services.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Plex Payload Sanitizer Tests")
public class PlexPayloadSanitizerTest {

    private final PlexPayloadSanitizer sanitizer = new PlexPayloadSanitizer();

    private static String fixture() throws IOException {
        try (InputStream in = PlexPayloadSanitizerTest.class.getResourceAsStream("/plex/media-play-episode.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @DisplayName("A real-shaped Plex payload is rebuilt from the allowlist only, in a fixed order")
    @Test
    public void rebuildsRealPayloadFromAllowlist() throws Exception {
        assertEquals("{\"event\":\"media.play\",\"user\":true,\"owner\":true,"
                + "\"Player\":{\"title\":\"Living Room TV\",\"local\":true},"
                + "\"Metadata\":{\"type\":\"episode\",\"title\":\"Cold Open\",\"grandparentTitle\":\"Slow Horses\","
                + "\"parentTitle\":\"Season 4\",\"parentIndex\":4,\"index\":2,\"year\":2025,"
                + "\"librarySectionTitle\":\"TV Shows\",\"duration\":2700000,\"viewOffset\":123000}}",
                sanitizer.sanitise(fixture()));
    }

    @DisplayName("Account, the WAN address, every uuid, artwork paths and ids never survive")
    @Test
    public void privateFieldsNeverSurvive() throws Exception {
        String clean = sanitizer.sanitise(fixture());

        for (String forbidden : List.of("Account", "alice", "publicAddress", "203.0.113.42", "uuid", "thumb",
                "avatar", "Server", "Home Plex", "token", "Token", "ratingKey", "guid", "Guid", "Role", "summary",
                "/library/", "addedAt")) {
            assertFalse(clean.contains(forbidden), () -> "payload leaked '" + forbidden + "': " + clean);
        }
    }

    @DisplayName("A nested object under an allowlisted key is dropped, not passed through")
    @Test
    public void nestedValuesUnderAllowlistedKeysAreDropped() {
        String clean = sanitizer.sanitise("{\"event\":{\"Account\":{\"title\":\"alice\"}},\"user\":[1],"
                + "\"Metadata\":{\"title\":{\"x\":1},\"type\":\"movie\",\"year\":[1997]}}");

        assertEquals("{\"Metadata\":{\"type\":\"movie\"}}", clean);
    }

    @DisplayName("Null values and keys that are absent are omitted")
    @Test
    public void nullsAndAbsentKeysAreOmitted() {
        assertEquals("{\"event\":\"media.stop\",\"Metadata\":{\"title\":\"Heat\"}}",
                sanitizer.sanitise("{\"event\":\"media.stop\",\"user\":null,\"Metadata\":{\"title\":\"Heat\","
                        + "\"year\":null}}"));
    }

    @DisplayName("A Player or Metadata section with nothing allowlisted is omitted")
    @Test
    public void emptySectionsAreOmitted() {
        assertEquals("{\"event\":\"media.play\"}",
                sanitizer.sanitise("{\"event\":\"media.play\",\"Player\":{\"uuid\":\"u\"},"
                        + "\"Metadata\":{\"guid\":\"g\"}}"));
    }

    @DisplayName("A Player or Metadata that is not an object is omitted")
    @Test
    public void nonObjectSectionsAreOmitted() {
        assertEquals("{\"event\":\"media.play\"}",
                sanitizer.sanitise("{\"event\":\"media.play\",\"Player\":\"x\",\"Metadata\":[{\"title\":\"Heat\"}]}"));
    }

    @DisplayName("A payload that is not a JSON object sanitises to an empty object")
    @Test
    public void nonObjectPayloadIsEmpty() {
        assertEquals("{}", sanitizer.sanitise("[1,2]"));
        assertEquals("{}", sanitizer.sanitise("\"text\""));
    }

    @DisplayName("A payload that is not JSON raises a JacksonException")
    @Test
    public void invalidJsonRaises() {
        assertThrows(JacksonException.class, () -> sanitizer.sanitise("not json"));
    }

    @DisplayName("Markup in a title is kept as text, escaped by JSON, for the client to render as text")
    @Test
    public void titleIsJsonEscaped() {
        assertEquals("{\"Metadata\":{\"title\":\"<img src=x onerror=alert(1)>\\\"q\\\"\"}}",
                sanitizer.sanitise("{\"Metadata\":{\"title\":\"<img src=x onerror=alert(1)>\\\"q\\\"\"}}"));
    }
}
