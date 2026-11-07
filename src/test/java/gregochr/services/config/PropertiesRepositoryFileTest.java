package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The settings file outside the app (the container's /config/application.properties): which file the repository
 * picks, and that a save replaces it atomically, keeps its permissions and never damages the original.
 */
@DisplayName("PropertiesRepository settings file")
public class PropertiesRepositoryFileTest {

    private static PropertiesRepository repositoryFor(Path file) {
        return new PropertiesRepository(
                new MockEnvironment().withProperty(PropertiesRepository.CONFIG_FILE_PROPERTY, file.toString()));
    }

    private static Path writeSettings(Path dir, String permissions) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.write(file, List.of("# Hue", "hue.start-scene=old-start", "sse.heartbeat-interval=10000"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
        return file;
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return children.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @DisplayName("with cinelight.config-file set, the repository uses that file, not the classpath one")
    @Test
    public void testConfigFilePropertyChoosesFile(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");

        assertEquals(file, repositoryFor(file).resolvePropertiesPath());
    }

    @DisplayName("readAllEntries lists the keys of the configured file, with values resolved from the environment")
    @Test
    public void testReadAllEntriesReadsConfiguredFile(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");
        MockEnvironment env = new MockEnvironment()
                .withProperty(PropertiesRepository.CONFIG_FILE_PROPERTY, file.toString())
                .withProperty("hue.start-scene", "scene-1");

        List<PropertySection> sections = new PropertiesRepository(env).readAllEntries();

        assertEquals(1, sections.size());
        assertEquals("Hue", sections.get(0).title());
        assertEquals("hue.start-scene", sections.get(0).entries().get(0).getKey());
        assertEquals("scene-1", sections.get(0).entries().get(0).getValue());
        assertEquals("sse.heartbeat-interval", sections.get(0).entries().get(1).getKey());
    }

    @DisplayName("a path with surrounding whitespace is trimmed")
    @Test
    public void testConfigFilePropertyIsTrimmed(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");
        PropertiesRepository repo = new PropertiesRepository(
                new MockEnvironment().withProperty(PropertiesRepository.CONFIG_FILE_PROPERTY, "  " + file + "  "));

        assertEquals(file, repo.resolvePropertiesPath());
    }

    @DisplayName("a blank cinelight.config-file keeps the classpath behaviour")
    @Test
    public void testBlankConfigFileUsesClasspath() throws IOException {
        PropertiesRepository repo = new PropertiesRepository(
                new MockEnvironment().withProperty(PropertiesRepository.CONFIG_FILE_PROPERTY, "   "));

        Path path = repo.resolvePropertiesPath();

        assertEquals("application.properties", path.getFileName().toString());
        assertTrue(path.toString().contains("test-classes"), path.toString());
    }

    @DisplayName("an unset cinelight.config-file keeps the classpath behaviour")
    @Test
    public void testUnsetConfigFileUsesClasspath() throws IOException {
        Path path = new PropertiesRepository(new MockEnvironment()).resolvePropertiesPath();

        assertTrue(path.toString().contains("test-classes"), path.toString());
    }

    @DisplayName("a save round-trips: the changed line is rewritten and every other line is kept")
    @Test
    public void testSaveRoundTrips(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");
        PropertiesRepository repo = repositoryFor(file);

        repo.saveProperties(Map.of("hue.start-scene", "new-start"), repo.resolvePropertiesPath());

        assertEquals(List.of("# Hue", "hue.start-scene=new-start", "sse.heartbeat-interval=10000"),
                Files.readAllLines(file));
    }

    @DisplayName("a save leaves only the settings file in the directory: no temp file is left behind")
    @Test
    public void testSaveLeavesNoTempFile(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");

        repositoryFor(file).saveProperties(Map.of("hue.start-scene", "x"), file);

        assertEquals(List.of("application.properties"), namesIn(dir));
    }

    @DisplayName("a save preserves the original's permissions, whether 0600, 0640, 0644 or read-only 0400")
    @Test
    public void testSavePreservesPermissions(@TempDir Path dir) throws IOException {
        for (String permissions : List.of("rw-------", "rw-r-----", "rw-r--r--", "r--------")) {
            Path sub = Files.createDirectory(dir.resolve("p-" + permissions));
            Path file = writeSettings(sub, permissions);

            repositoryFor(file).saveProperties(Map.of("hue.start-scene", "after-" + permissions), file);

            assertEquals(PosixFilePermissions.fromString(permissions), Files.getPosixFilePermissions(file),
                    "permissions after save for " + permissions);
            assertTrue(Files.readAllLines(file).contains("hue.start-scene=after-" + permissions), permissions);
        }
    }

    @DisplayName("the new content is fully written to a temp file in the same directory, with the original's "
            + "permissions, while the original is still intact, and only then moved over it")
    @Test
    public void testWritesTempFileInSameDirectoryBeforeMove(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r-----");
        String original = Files.readString(file);
        List<String> seen = new ArrayList<>();
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment()) {
            @Override
            void moveIntoPlace(Path temp, Path target) throws IOException {
                seen.add("tempDir=" + temp.getParent());
                seen.add("tempContent=" + Files.readString(temp));
                seen.add("tempPerms=" + PosixFilePermissions.toString(Files.getPosixFilePermissions(temp)));
                seen.add("targetStillOriginal=" + Files.readString(target).equals(original));
                super.moveIntoPlace(temp, target);
            }
        };

        repo.saveProperties(Map.of("hue.start-scene", "new"), file);

        assertEquals(List.of(
                "tempDir=" + dir,
                "tempContent=# Hue\nhue.start-scene=new\nsse.heartbeat-interval=10000\n",
                "tempPerms=rw-r-----",
                "targetStillOriginal=true"), seen);
        assertEquals("# Hue\nhue.start-scene=new\nsse.heartbeat-interval=10000\n", Files.readString(file));
    }

    @DisplayName("a failure while moving into place keeps the original intact and removes the temp file")
    @Test
    public void testFailedMoveKeepsOriginalAndCleansUp(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");
        String original = Files.readString(file);
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment()) {
            @Override
            void moveIntoPlace(Path temp, Path target) throws IOException {
                throw new IOException("disk full");
            }
        };

        IOException thrown = assertThrows(IOException.class,
                () -> repo.saveProperties(Map.of("hue.start-scene", "lost"), file));

        assertEquals("disk full", thrown.getMessage());
        assertEquals(original, Files.readString(file));
        assertEquals(List.of("application.properties"), namesIn(dir));
    }

    @DisplayName("an unexpected runtime failure while moving also keeps the original and removes the temp file")
    @Test
    public void testRuntimeFailureKeepsOriginalAndCleansUp(@TempDir Path dir) throws IOException {
        Path file = writeSettings(dir, "rw-r--r--");
        String original = Files.readString(file);
        PropertiesRepository repo = new PropertiesRepository(new MockEnvironment()) {
            @Override
            void moveIntoPlace(Path temp, Path target) {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> repo.saveProperties(Map.of("hue.start-scene", "lost"), file));

        assertEquals(original, Files.readString(file));
        assertEquals(List.of("application.properties"), namesIn(dir));
    }

    @DisplayName("a directory that cannot be written to fails the save and leaves the original untouched")
    @Test
    public void testUnwritableDirectoryKeepsOriginal(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores directory permissions");
        Path file = writeSettings(dir, "rw-r--r--");
        String original = Files.readString(file);
        Files.setPosixFilePermissions(dir, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            assertThrows(IOException.class,
                    () -> repositoryFor(file).saveProperties(Map.of("hue.start-scene", "lost"), file));
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }

        assertEquals(original, Files.readString(file));
        assertFalse(namesIn(dir).stream().anyMatch(name -> name.endsWith(".tmp")));
    }
}
