package gregochr.services.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PropertiesRepository {
    /**
     * When set (the container sets it through {@code CINELIGHT_CONFIG_FILE}), the settings file is this path
     * outside the app. When unset, development keeps reading and writing the classpath file.
     */
    public static final String CONFIG_FILE_PROPERTY = "cinelight.config-file";

    private static final Logger LOGGER = LogManager.getLogger(PropertiesRepository.class);

    private final Environment environment;
    private final Resource propertiesResource;

    @Autowired
    public PropertiesRepository(Environment environment) {
        this(environment, settingsResource(environment));
    }

    private static Resource settingsResource(Environment environment) {
        String configFile = environment.getProperty(CONFIG_FILE_PROPERTY);
        if (configFile == null || configFile.isBlank()) {
            return new ClassPathResource("application.properties");
        }
        return new FileSystemResource(configFile.trim());
    }

    /** Lets tests supply the properties file, so unreadable and unusual files can be exercised. */
    PropertiesRepository(Environment environment, Resource propertiesResource) {
        this.environment = environment;
        this.propertiesResource = propertiesResource;
    }

    public List<PropertySection> readAllEntries() {
        List<PropertySection> sections = new ArrayList<>();
        String currentTitle = "General";
        List<Map.Entry<String, String>> currentEntries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        propertiesResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    if (!currentEntries.isEmpty()) {
                        sections.add(new PropertySection(currentTitle, currentEntries));
                        currentEntries = new ArrayList<>();
                    }
                    currentTitle = trimmed.substring(1).trim();
                } else if (trimmed.contains("=")) {
                    int equalsIndex = trimmed.indexOf('=');
                    String key = trimmed.substring(0, equalsIndex).trim();
                    String value = resolveValue(key);
                    currentEntries.add(new AbstractMap.SimpleEntry<>(key, value));
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read application.properties: {}", e.getMessage());
            return sections;
        }

        if (!currentEntries.isEmpty()) {
            sections.add(new PropertySection(currentTitle, currentEntries));
        }

        return sections;
    }

    /**
     * The settings file's own text, key to value, in file order. It follows exactly the rules
     * {@link #saveProperties} uses: blank lines and lines starting with {@code #} are skipped, a line is split on its
     * first {@code =}, the key and the value are trimmed, and when a key appears twice the last one wins (as in
     * Spring).
     *
     * <p>This is the file's text under CineLight's own rules, <em>not</em> the effective configuration. It never
     * reads the {@link Environment}, which does not see a save until restart. Environment overrides, {@code :} or
     * space separators, {@code !} comments, {@code \} continuations and {@code ${...}} placeholders are not
     * interpreted. A line with an empty key is skipped. Unlike {@link #readAllEntries()}, an unreadable file is
     * an error, never an empty result, so a caller cannot mistake it for a file with no settings.
     *
     * @throws java.io.UncheckedIOException if the file cannot be read
     */
    public Map<String, String> readFileEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(propertiesResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                // eq == 0 is an empty key ("=value"): skipped, as a line with no "=" at all is.
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                    continue;
                }
                entries.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the settings file", e);
        }
        return entries;
    }

    public Map<String, Boolean> readBooleanProperties(String prefix) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        propertiesResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(prefix) && trimmed.contains("=")) {
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim();
                    String value = resolveValue(key);
                    result.put(key, BooleanProperties.parse(value));
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read boolean properties with prefix '{}': {}", prefix, e.getMessage());
        }
        return result;
    }

    public Map<String, String> readSceneProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        String startScene = environment.getProperty("hue.start-scene");
        String stopScene = environment.getProperty("hue.stop-scene");
        result.put("hue.start-scene", startScene != null ? startScene : "");
        result.put("hue.stop-scene", stopScene != null ? stopScene : "");
        return result;
    }

    public void saveProperties(Map<String, String> updates, Path propertiesFile) throws IOException {
        List<String> lines = Files.readAllLines(propertiesFile);
        List<String> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                int eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim();
                if (updates.containsKey(key)) {
                    output.add(key + "=" + updates.get(key));
                    continue;
                }
            }
            output.add(line);
        }
        writeAtomically(propertiesFile, output);
    }

    /**
     * Replaces {@code target} without ever leaving it half-written: the new content goes to a temp file in the
     * same directory (a rename is atomic only within one filesystem), the original's POSIX permissions are copied
     * onto it, it is fsynced, and only then moved over the original. A failure at any point deletes the temp file
     * and leaves the original untouched. The directory, not the file, is what the container bind-mounts, because
     * renaming onto a bind-mounted file fails with EBUSY.
     */
    private void writeAtomically(Path target, List<String> lines) throws IOException {
        byte[] content = (String.join(System.lineSeparator(), lines) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        Path directory = target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                // Set after the content is written: an original that is read-only (0400) must not stop us
                // writing its replacement.
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target));
                channel.force(true);
            }
            moveIntoPlace(temp, target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /** The final step of a save; its own method so tests can make it fail. */
    void moveIntoPlace(Path temp, Path target) throws IOException {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path resolvePropertiesPath() throws IOException {
        return propertiesResource.getFile().toPath();
    }

    String resolveValue(String key) {
        String envValue = environment.getProperty(key);
        return envValue != null ? envValue : "";
    }
}
