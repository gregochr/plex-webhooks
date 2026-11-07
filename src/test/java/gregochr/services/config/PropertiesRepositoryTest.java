package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("PropertiesRepository Tests")
public class PropertiesRepositoryTest {

    private PropertiesRepository repository;

    @BeforeEach
    public void setUp() {
        Environment environment = mock(Environment.class);
        repository = new PropertiesRepository(environment);
    }

    @DisplayName("saveProperties updates matching lines in file")
    @Test
    public void testSavePropertiesUpdatesLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of(
                "plex.movie-play-enabled=true",
                "plex.movie-stop-enabled=true"
        ));

        repository.saveProperties(Map.of("plex.movie-play-enabled", "false"), file);

        List<String> result = Files.readAllLines(file);
        assertEquals("plex.movie-play-enabled=false", result.get(0));
        assertEquals("plex.movie-stop-enabled=true", result.get(1));
    }

    @DisplayName("saveProperties preserves comments and non-matching lines")
    @Test
    public void testSavePropertiesPreservesComments(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of(
                "# PLEX Movie Command Configuration",
                "plex.movie-play-enabled=true",
                "",
                "# Other section",
                "other.key=value"
        ));

        repository.saveProperties(Map.of("plex.movie-play-enabled", "false"), file);

        List<String> result = Files.readAllLines(file);
        assertEquals("# PLEX Movie Command Configuration", result.get(0));
        assertEquals("plex.movie-play-enabled=false", result.get(1));
        assertEquals("", result.get(2));
        assertEquals("# Other section", result.get(3));
        assertEquals("other.key=value", result.get(4));
    }

    @DisplayName("readAllEntries groups entries under the preceding comment title, defaulting to General")
    @Test
    public void testReadAllEntriesGroupsBySectionTitle() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("postcode", "DH1 4HU")
                .withProperty("dashboard.admin-tab-enabled", "true");
        PropertiesRepository repo = new PropertiesRepository(env);

        List<PropertySection> sections = repo.readAllEntries();

        assertEquals("General", sections.get(0).title());
        assertEquals(2, sections.get(0).entries().size());
        assertEquals("postcode", sections.get(0).entries().get(0).getKey());
        assertEquals("DH1 4HU", sections.get(0).entries().get(0).getValue());
        assertEquals("debug", sections.get(0).entries().get(1).getKey());
        assertEquals("", sections.get(0).entries().get(1).getValue());
        assertEquals("Dashboard Configuration", sections.get(1).title());
        assertEquals("dashboard.admin-tab-enabled", sections.get(1).entries().get(0).getKey());
        assertEquals("true", sections.get(1).entries().get(0).getValue());
        assertEquals("Security Configuration", sections.get(sections.size() - 1).title());
        assertEquals(2, sections.get(sections.size() - 1).entries().size());
    }

    @DisplayName("readBooleanProperties returns only prefixed keys with true/false resolved from the environment")
    @Test
    public void testReadBooleanPropertiesFiltersByPrefix() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "TRUE")
                .withProperty("plex.movie-scrobble-enabled", "false");
        PropertiesRepository repo = new PropertiesRepository(env);

        Map<String, Boolean> result = repo.readBooleanProperties("plex.movie-");

        assertEquals(5, result.size());
        assertEquals(Boolean.TRUE, result.get("plex.movie-play-enabled"));
        assertEquals(Boolean.FALSE, result.get("plex.movie-scrobble-enabled"));
        assertEquals(Boolean.FALSE, result.get("plex.movie-stop-enabled"));
    }

    @DisplayName("readBooleanProperties returns an empty map when no key matches the prefix")
    @Test
    public void testReadBooleanPropertiesNoMatch() {
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment());

        assertTrue(repo.readBooleanProperties("nonexistent.").isEmpty());
    }

    @DisplayName("readSceneProperties returns configured scene ids")
    @Test
    public void testReadScenePropertiesConfigured() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("hue.start-scene", "start-id")
                .withProperty("hue.stop-scene", "stop-id");

        Map<String, String> result = new PropertiesRepository(env).readSceneProperties();

        assertEquals(Map.of("hue.start-scene", "start-id", "hue.stop-scene", "stop-id"), result);
    }

    @DisplayName("readSceneProperties returns empty strings when scenes are unset")
    @Test
    public void testReadScenePropertiesUnset() {
        Map<String, String> result = new PropertiesRepository(new MockEnvironment()).readSceneProperties();

        assertEquals(Map.of("hue.start-scene", "", "hue.stop-scene", ""), result);
    }

    @DisplayName("resolveValue returns the environment value, or empty string when absent")
    @Test
    public void testResolveValue() {
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment().withProperty("a.key", "v"));

        assertEquals("v", repo.resolveValue("a.key"));
        assertEquals("", repo.resolveValue("missing.key"));
    }

    @DisplayName("resolvePropertiesPath points at the classpath application.properties file")
    @Test
    public void testResolvePropertiesPath() throws IOException {
        Path path = repository.resolvePropertiesPath();

        assertEquals("application.properties", path.getFileName().toString());
        assertTrue(Files.exists(path));
    }
}
