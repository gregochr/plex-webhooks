package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code readFileEntries}: the file's text under the rules {@code saveProperties} uses, never the Environment. */
@DisplayName("PropertiesRepository readFileEntries")
public class PropertiesRepositoryFileEntriesTest {

    private static Map<String, String> read(String text) {
        Resource resource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
        return new PropertiesRepository(new MockEnvironment(), resource).readFileEntries();
    }

    @DisplayName("It reads application.properties.example: every entry, raw values, no comments, file order")
    @Test
    public void readsTheExampleFile() {
        Map<String, String> entries = new PropertiesRepository(new MockEnvironment(),
                new ClassPathResource("application.properties.example")).readFileEntries();

        assertEquals(30, entries.size());
        assertEquals("postcode", entries.keySet().iterator().next());
        assertEquals("DH1 4HU", entries.get("postcode"));
        assertEquals("10000", entries.get("sse.heartbeat-interval"));
        assertEquals("YOUR_HUE_APPLICATION_KEY", entries.get("hue.application-key"));
        assertEquals("false", entries.get("plex.clip-scrobble-enabled"));
        assertEquals("${CINELIGHT_ADMIN_PASSWORD}", entries.get("app.admin.password"));
        assertEquals(List.of("app.admin.username", "app.admin.password"),
                List.copyOf(entries.keySet()).subList(28, 30));
    }

    @DisplayName("It never reads the Environment: a value there is ignored, and so is a key only the Environment has")
    @Test
    public void ignoresTheEnvironment() {
        MockEnvironment environment = new MockEnvironment().withProperty("hue.hub-address", "from-environment")
                .withProperty("only.in.environment", "x");
        Resource resource = new ByteArrayResource("hue.hub-address=from-file".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = new PropertiesRepository(environment, resource).readFileEntries();

        assertEquals(Map.of("hue.hub-address", "from-file"), entries);
    }

    @DisplayName("When a key appears twice the last one wins, and keeps the position of the first")
    @Test
    public void lastDuplicateWins() {
        Map<String, String> entries = read("a=1\nb=2\na=3\n");

        assertEquals(Map.of("a", "3", "b", "2"), entries);
        assertEquals(List.of("a", "b"), List.copyOf(entries.keySet()));
    }

    @DisplayName("A line is split on its FIRST equals sign, so a value may contain further equals signs")
    @Test
    public void valueContainingEquals() {
        Map<String, String> entries = read("url=jdbc:postgresql://h/db?a=b&c=d\nbase64=QUJD==\n");

        assertEquals("jdbc:postgresql://h/db?a=b&c=d", entries.get("url"));
        assertEquals("QUJD==", entries.get("base64"));
    }

    @DisplayName("Keys and values are trimmed, and an empty value is kept as the empty string")
    @Test
    public void trimsAndKeepsEmptyValues() {
        Map<String, String> entries = read("  spaced.key  =  spaced value  \nempty.value=\nempty.spaces=   \n");

        assertEquals(Map.of("spaced.key", "spaced value", "empty.value", "", "empty.spaces", ""), entries);
    }

    @DisplayName("Blank lines, # comments (even indented, even with an equals sign) and lines without = are skipped")
    @Test
    public void skipsBlanksCommentsAndJunk() {
        Map<String, String> entries = read("\n   \n# a=1\n   # b=2\nno separator here\nreal=yes\n! c=3\n");

        assertEquals(Map.of("real", "yes", "! c", "3"), entries);
    }

    @DisplayName("A line with an empty key is skipped")
    @Test
    public void skipsEmptyKeys() {
        assertEquals(Map.of("ok", "1"), read("=orphan\n  =also orphan\nok=1\n"));
    }

    @DisplayName("It reads UTF-8, and Windows line endings")
    @Test
    public void utf8AndCrLf() {
        assertEquals(Map.of("title", "Amélie", "next", "1"), read("title=Amélie\r\nnext=1\r\n"));
    }

    @DisplayName("An empty file is an empty map")
    @Test
    public void emptyFile() {
        assertEquals(Map.of(), read(""));
    }

    @DisplayName("An unreadable file is an error, not an empty map, so a caller cannot mistake it for no settings")
    @Test
    public void unreadableFileThrows() throws IOException {
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("disk gone"));
        PropertiesRepository repository = new PropertiesRepository(new MockEnvironment(), broken);

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, repository::readFileEntries);

        assertEquals("Failed to read the settings file", thrown.getMessage());
    }
}
