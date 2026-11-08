package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @DisplayName("readBooleanProperties reads yes, on and 1 as true and no, off, 0 as false, like startup does")
    @Test
    public void testReadBooleanPropertiesSpringSemantics() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "yes")
                .withProperty("plex.movie-resume-enabled", "on")
                .withProperty("plex.movie-stop-enabled", "1")
                .withProperty("plex.movie-pause-enabled", "off")
                .withProperty("plex.movie-scrobble-enabled", "0");

        Map<String, Boolean> result = new PropertiesRepository(env).readBooleanProperties("plex.movie-");

        assertEquals(Map.of(
                "plex.movie-play-enabled", true,
                "plex.movie-resume-enabled", true,
                "plex.movie-stop-enabled", true,
                "plex.movie-pause-enabled", false,
                "plex.movie-scrobble-enabled", false), result);
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

    private static PropertiesRepository repositoryFor(String fileContent, MockEnvironment environment) {
        return new PropertiesRepository(environment,
                new ByteArrayResource(fileContent.getBytes(StandardCharsets.UTF_8)));
    }

    private static PropertiesRepository unreadableRepository() {
        return new PropertiesRepository(new MockEnvironment(), new ClassPathResource("no-such-file.properties"));
    }

    @DisplayName("readAllEntries returns no sections when the properties file cannot be read")
    @Test
    public void testReadAllEntriesUnreadableFile() {
        assertTrue(unreadableRepository().readAllEntries().isEmpty());
    }

    /** A properties file that yields the given text and then fails with an IOException. */
    private static Resource failingAfter(String text) {
        byte[] head = text.getBytes(StandardCharsets.UTF_8);
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "fails after its first lines";
            }

            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private int position;

                    @Override
                    public int read() throws IOException {
                        if (position == head.length) {
                            throw new IOException("disk error");
                        }
                        return head[position++];
                    }
                };
            }
        };
    }

    @DisplayName("readAllEntries keeps the sections completed before a read failure")
    @Test
    public void testReadAllEntriesKeepsSectionsBeforeReadFailure() {
        Resource failingMidway = failingAfter("a=1\n# Next\nb=2\n");
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment().withProperty("a", "1"),
                failingMidway);

        List<PropertySection> sections = repo.readAllEntries();

        assertEquals(1, sections.size());
        assertEquals("General", sections.get(0).title());
        assertEquals("a", sections.get(0).entries().get(0).getKey());
    }

    @DisplayName("readBooleanProperties keeps the entries read before a read failure")
    @Test
    public void testReadBooleanPropertiesKeepsEntriesBeforeReadFailure() {
        Resource failingMidway = failingAfter("plex.movie-play-enabled=true\n");
        PropertiesRepository repo = new PropertiesRepository(
                new MockEnvironment().withProperty("plex.movie-play-enabled", "true"), failingMidway);

        assertEquals(Map.of("plex.movie-play-enabled", true), repo.readBooleanProperties("plex.movie-"));
    }

    @DisplayName("readAllEntries skips a comment that directly follows another, so no empty section appears")
    @Test
    public void testReadAllEntriesConsecutiveComments() {
        PropertiesRepository repo = repositoryFor("# First\n# Second\nkey=value\n", new MockEnvironment());

        List<PropertySection> sections = repo.readAllEntries();

        assertEquals(1, sections.size());
        assertEquals("Second", sections.get(0).title());
    }

    @DisplayName("readAllEntries adds no empty section for a trailing comment")
    @Test
    public void testReadAllEntriesTrailingComment() {
        PropertiesRepository repo = repositoryFor("key=value\n# Trailing\n", new MockEnvironment());

        List<PropertySection> sections = repo.readAllEntries();

        assertEquals(1, sections.size());
        assertEquals("General", sections.get(0).title());
    }

    @DisplayName("readAllEntries ignores lines without an equals sign")
    @Test
    public void testReadAllEntriesIgnoresLinesWithoutEquals() {
        PropertiesRepository repo = repositoryFor("not a property\nkey=value\n", new MockEnvironment());

        List<PropertySection> sections = repo.readAllEntries();

        assertEquals(1, sections.get(0).entries().size());
        assertEquals("key", sections.get(0).entries().get(0).getKey());
    }

    @DisplayName("readAllEntries splits at the first equals sign and resolves the value from the environment")
    @Test
    public void testReadAllEntriesSplitsAtFirstEquals() {
        MockEnvironment env = new MockEnvironment().withProperty("url", "http://host/?a=b");
        PropertiesRepository repo = repositoryFor("  url = ${URL:http://x/?q=1}\n", env);

        PropertySection section = repo.readAllEntries().get(0);

        assertEquals("url", section.entries().get(0).getKey());
        assertEquals("http://host/?a=b", section.entries().get(0).getValue());
    }

    @DisplayName("readAllEntries returns no sections for an empty file")
    @Test
    public void testReadAllEntriesEmptyFile() {
        assertTrue(repositoryFor("\n  \n", new MockEnvironment()).readAllEntries().isEmpty());
    }

    @DisplayName("readBooleanProperties returns an empty map when the properties file cannot be read")
    @Test
    public void testReadBooleanPropertiesUnreadableFile() {
        assertTrue(unreadableRepository().readBooleanProperties("plex.movie-").isEmpty());
    }

    @DisplayName("readBooleanProperties ignores a prefixed line that has no equals sign")
    @Test
    public void testReadBooleanPropertiesPrefixedLineWithoutEquals() {
        PropertiesRepository repo = repositoryFor("plex.movie-play-enabled\nplex.movie-stop-enabled=true\n",
                new MockEnvironment().withProperty("plex.movie-stop-enabled", "true"));

        assertEquals(Map.of("plex.movie-stop-enabled", true), repo.readBooleanProperties("plex.movie-"));
    }

    @DisplayName("readBooleanProperties reads a value that is not a Spring true value as false")
    @Test
    public void testReadBooleanPropertiesNonBooleanValue() {
        PropertiesRepository repo = repositoryFor("plex.movie-play-enabled=maybe\n",
                new MockEnvironment().withProperty("plex.movie-play-enabled", "maybe"));

        assertEquals(Map.of("plex.movie-play-enabled", false), repo.readBooleanProperties("plex.movie-"));
    }

    @DisplayName("readBooleanProperties does not match a key that only shares the start of the prefix")
    @Test
    public void testReadBooleanPropertiesPrefixBoundary() {
        PropertiesRepository repo = repositoryFor("plex.movie=true\nplex.movie-play-enabled=true\n",
                new MockEnvironment().withProperty("plex.movie-play-enabled", "true"));

        assertEquals(Map.of("plex.movie-play-enabled", true), repo.readBooleanProperties("plex.movie-"));
    }

    @DisplayName("saveProperties leaves a line alone when its key is not in the updates")
    @Test
    public void testSavePropertiesKeepsUnlistedKey(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1", "b=2"));

        repository.saveProperties(Map.of("a", "1"), file);

        assertEquals(List.of("a=1", "b=2"), Files.readAllLines(file));
    }

    @DisplayName("saveProperties does not rewrite a comment that mentions a key, or a line without an equals sign")
    @Test
    public void testSavePropertiesKeepsCommentsAndPlainLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("# a=1", "a", "a=1"));

        repository.saveProperties(Map.of("a", "9"), file);

        assertEquals(List.of("# a=1", "a", "a=9"), Files.readAllLines(file));
    }

    @DisplayName("saveProperties rewrites a spaced line as key=value")
    @Test
    public void testSavePropertiesNormalisesSpacing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("  a = 1"));

        repository.saveProperties(Map.of("a", "2"), file);

        assertEquals(List.of("a=2"), Files.readAllLines(file));
    }

    @DisplayName("saveProperties appends a key that is missing from the file and persists it")
    @Test
    public void testSavePropertiesAppendsMissingKey(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("# Section", "a=1"));

        repository.saveProperties(Map.of("z", "26"), file);

        assertEquals(List.of("# Section", "a=1", "z=26"), Files.readAllLines(file));
    }

    @DisplayName("saveProperties rewrites a present key in place and does not duplicate it")
    @Test
    public void testSavePropertiesPresentKeyNotDuplicated(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1", "# note", "b=2"));

        repository.saveProperties(Map.of("a", "9"), file);

        assertEquals(List.of("a=9", "# note", "b=2"), Files.readAllLines(file));
    }

    @DisplayName("saveProperties with present and absent keys rewrites the first and appends the rest in order")
    @Test
    public void testSavePropertiesMixedPresentAndAbsent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1", "# note", "b=2"));
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("y", "25");
        updates.put("b", "20");
        updates.put("x", "24");

        repository.saveProperties(updates, file);

        assertEquals(List.of("a=1", "# note", "b=20", "y=25", "x=24"), Files.readAllLines(file));
    }

    @DisplayName("a saved value is what the next read returns, for values, booleans and scenes")
    @Test
    public void testReadsSeeSavedValues(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("# Plex", "plex.movie-play-enabled=false", "# Hue", "hue.start-scene=old"));
        MockEnvironment env = new MockEnvironment()
                .withProperty("plex.movie-play-enabled", "false")
                .withProperty("hue.start-scene", "old");
        PropertiesRepository repo = new PropertiesRepository(env, new FileSystemResource(file));

        repo.saveProperties(Map.of("plex.movie-play-enabled", "true", "hue.start-scene", "new",
                "hue.stop-scene", "stop-new"), file);

        assertEquals("true", repo.resolveValue("plex.movie-play-enabled"));
        assertEquals(Map.of("plex.movie-play-enabled", true), repo.readBooleanProperties("plex.movie-"));
        assertEquals(Map.of("hue.start-scene", "new", "hue.stop-scene", "stop-new"), repo.readSceneProperties());
        assertEquals("true", repo.readAllEntries().get(0).entries().get(0).getValue());
    }

    @DisplayName("a failed save leaves reads on the old value")
    @Test
    public void testFailedSaveKeepsOldReads(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("hue.start-scene=old"));
        MockEnvironment env = new MockEnvironment().withProperty("hue.start-scene", "old");
        PropertiesRepository repo = new PropertiesRepository(env, new FileSystemResource(file)) {
            @Override
            void moveIntoPlace(Path temp, Path target) throws IOException {
                throw new IOException("disk full");
            }
        };

        assertThrows(IOException.class, () -> repo.saveProperties(Map.of("hue.start-scene", "lost"), file));

        assertEquals("old", repo.resolveValue("hue.start-scene"));
        assertEquals("old", repo.readSceneProperties().get("hue.start-scene"));
    }

    @DisplayName("saveProperties refuses a bad key before writing: file and reads unchanged")
    @ParameterizedTest
    @ValueSource(strings = {"a\nb", "a\rb", "a=b", "a:b", "a#b", "a!b", "a b", "a\tb", "", "  "})
    public void testSavePropertiesRejectsBadKey(String badKey, @TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1"));
        byte[] before = Files.readAllBytes(file);
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment().withProperty("a", "1"),
                new FileSystemResource(file));
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("a", "2");
        updates.put(badKey, "x");

        assertThrows(IllegalArgumentException.class, () -> repo.saveProperties(updates, file));

        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("1", repo.resolveValue("a"));
        assertEquals("", repo.resolveValue(badKey));
    }

    @DisplayName("saveProperties refuses a value with a line break, leaving file and reads unchanged")
    @ParameterizedTest
    @ValueSource(strings = {"x\ny=1", "x\ry=1"})
    public void testSavePropertiesRejectsBadValue(String badValue, @TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1"));
        byte[] before = Files.readAllBytes(file);
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment().withProperty("a", "1"),
                new FileSystemResource(file));

        assertThrows(IllegalArgumentException.class, () -> repo.saveProperties(Map.of("a", badValue), file));

        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("1", repo.resolveValue("a"));
    }

    @DisplayName("saveProperties refuses a null value")
    @Test
    public void testSavePropertiesRejectsNullValue(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.write(file, List.of("a=1"));
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("a", null);

        assertThrows(IllegalArgumentException.class, () -> repository.saveProperties(updates, file));
    }

    @DisplayName("saveProperties raises an IOException when the file does not exist")
    @Test
    public void testSavePropertiesMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.properties");

        assertThrows(IOException.class, () -> repository.saveProperties(Map.of("a", "1"), missing));
    }

    @DisplayName("resolvePropertiesPath returns the path of a file-backed resource")
    @Test
    public void testResolvePropertiesPathFileResource(@TempDir Path tempDir) throws IOException {
        Path file = Files.writeString(tempDir.resolve("app.properties"), "a=1");
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment(), new FileSystemResource(file));

        assertEquals(file, repo.resolvePropertiesPath());
    }

    @DisplayName("resolvePropertiesPath raises an IOException when the resource is not a file")
    @Test
    public void testResolvePropertiesPathNotAFile() {
        PropertiesRepository repo = repositoryFor("a=1", new MockEnvironment());

        assertThrows(IOException.class, repo::resolvePropertiesPath);
    }

    @DisplayName("resolvePropertiesPath points at the classpath application.properties file")
    @Test
    public void testResolvePropertiesPath() throws IOException {
        Path path = repository.resolvePropertiesPath();

        assertEquals("application.properties", path.getFileName().toString());
        assertTrue(Files.exists(path));
    }
}
