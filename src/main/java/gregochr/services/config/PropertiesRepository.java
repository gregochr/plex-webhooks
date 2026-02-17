package gregochr.services.config;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PropertiesRepository {

    private final Environment environment;

    public PropertiesRepository(Environment environment) {
        this.environment = environment;
    }

    public List<PropertySection> readAllEntries() {
        List<PropertySection> sections = new ArrayList<>();
        String currentTitle = "General";
        List<Map.Entry<String, String>> currentEntries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource("application.properties").getInputStream()))) {
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
                new InputStreamReader(new ClassPathResource("application.properties").getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(prefix) && trimmed.contains("=")) {
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim();
                    String value = resolveValue(key);
                    result.put(key, "true".equalsIgnoreCase(value));
                }
            }
        } catch (IOException e) {
            // return empty map
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
        Files.write(propertiesFile, output);
    }

    public Path resolvePropertiesPath() throws IOException {
        return new ClassPathResource("application.properties").getFile().toPath();
    }

    String resolveValue(String key) {
        String envValue = environment.getProperty(key);
        return envValue != null ? envValue : "";
    }
}
