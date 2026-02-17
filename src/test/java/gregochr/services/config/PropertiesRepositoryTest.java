package gregochr.services.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
