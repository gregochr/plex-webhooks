package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataNotFoundException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container starts with {@code SPRING_CONFIG_IMPORT=file:/config/application.properties}, deliberately without
 * {@code optional:}. A missing settings file must stop startup and say which path was missing, rather than the app
 * running on defaults with no Hue key.
 */
@Tag("integration")
@DisplayName("External settings file import")
public class ConfigImportStartupTest {

    @Configuration
    static class EmptyApplication {
    }

    private static ConfigurableApplicationContext start(String importLocation, String... extraArgs) {
        String[] args = new String[extraArgs.length + 1];
        args[0] = "--spring.config.import=" + importLocation;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        return new SpringApplicationBuilder(EmptyApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .run(args);
    }

    @DisplayName("a missing settings file fails startup with ConfigDataNotFoundException naming the path")
    @Test
    public void testMissingImportFailsStartup(@TempDir Path dir) {
        Path missing = dir.resolve("config").resolve("application.properties");

        ConfigDataNotFoundException thrown = assertThrows(ConfigDataNotFoundException.class,
                () -> start("file:" + missing));

        assertTrue(thrown.getMessage().contains(missing.toString()), thrown.getMessage());
    }

    @DisplayName("an existing settings file is imported, so its values reach the Environment")
    @Test
    public void testExistingImportIsLoaded(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("application.properties"),
                "hue.hub-address=10.9.8.7\nplex.movie-play-enabled=false\n");

        try (ConfigurableApplicationContext context = start("file:" + file)) {
            assertEquals("10.9.8.7", context.getEnvironment().getProperty("hue.hub-address"));
            assertEquals("false", context.getEnvironment().getProperty("plex.movie-play-enabled"));
        }
    }

    @DisplayName("the path in cinelight.config-file reaches the Environment and selects the file")
    @Test
    public void testConfigFilePropertyReachesEnvironment(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("application.properties"), "a=1\n");

        try (ConfigurableApplicationContext context = start("file:" + file,
                "--" + PropertiesRepository.CONFIG_FILE_PROPERTY + "=" + file)) {
            PropertiesRepository repository = new PropertiesRepository(context.getEnvironment());

            assertEquals(file, repository.resolvePropertiesPath());
        }
    }
}
