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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /**
     * Values saved since startup. The Spring {@link Environment} is fixed at startup, so reads consult this map
     * first; otherwise a saved setting would read back stale until the next restart.
     */
    private final Map<String, String> savedValues = new ConcurrentHashMap<>();

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
        result.put("hue.start-scene", resolveValue("hue.start-scene"));
        result.put("hue.stop-scene", resolveValue("hue.stop-scene"));
        return result;
    }

    public void saveProperties(Map<String, String> updates, Path propertiesFile) throws IOException {
        for (Map.Entry<String, String> update : updates.entrySet()) {
            requireSingleLine(update.getKey(), update.getValue());
        }
        List<String> lines = Files.readAllLines(propertiesFile);
        List<String> output = new ArrayList<>(lines.size() + updates.size());
        Set<String> written = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                int eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim();
                if (updates.containsKey(key)) {
                    output.add(key + "=" + updates.get(key));
                    written.add(key);
                    continue;
                }
            }
            output.add(line);
        }
        // A key the file does not mention yet is appended rather than silently dropped.
        for (Map.Entry<String, String> update : updates.entrySet()) {
            if (!written.contains(update.getKey())) {
                output.add(update.getKey() + "=" + update.getValue());
            }
        }
        writeAtomically(propertiesFile, output);
        // Only after the file is safely replaced: a failed save must not change what reads return.
        savedValues.putAll(updates);
    }

    /**
     * Backstop against injecting extra settings lines: a key or value with a line break, or a key that is blank
     * or holds a properties separator, comment marker or whitespace, is refused before anything is written.
     */
    private static void requireSingleLine(String key, String value) {
        if (key == null || key.isBlank() || key.chars().anyMatch(
                c -> c == '=' || c == ':' || c == '#' || c == '!' || Character.isWhitespace(c))) {
            throw new IllegalArgumentException("Invalid settings key");
        }
        if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid settings value for " + key);
        }
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
        String saved = savedValues.get(key);
        if (saved != null) {
            return saved;
        }
        String envValue = environment.getProperty(key);
        return envValue != null ? envValue : "";
    }
}
