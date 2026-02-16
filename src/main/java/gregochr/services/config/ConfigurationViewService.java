package gregochr.services.config;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigurationViewService {

    private static final Set<String> HIDDEN_KEYS = Set.of("hue.start-scene", "hue.stop-scene");

    private final Environment environment;

    public ConfigurationViewService(Environment environment) {
        this.environment = environment;
    }

    public List<ConfigurationViewSection> getConfigurationSections() {
        List<ConfigurationViewSection> sections = new ArrayList<>();
        String currentTitle = "General";
        List<ConfigurationViewItem> currentItems = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource("application.properties").getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    if (!currentItems.isEmpty()) {
                        sections.add(new ConfigurationViewSection(currentTitle, currentItems, isEditable(currentItems)));
                        currentItems = new ArrayList<>();
                    }
                    currentTitle = trimmed.substring(1).trim();
                } else if (trimmed.contains("=")) {
                    int equalsIndex = trimmed.indexOf('=');
                    String key = trimmed.substring(0, equalsIndex).trim();
                    if (HIDDEN_KEYS.contains(key)) {
                        continue;
                    }
                    String value = resolveValue(key);
                    currentItems.add(buildItem(key, value));
                }
            }
        } catch (IOException e) {
            return sections;
        }

        if (!currentItems.isEmpty()) {
            sections.add(new ConfigurationViewSection(currentTitle, currentItems, isEditable(currentItems)));
        }

        return sections;
    }

    boolean isEditable(List<ConfigurationViewItem> items) {
        return !items.isEmpty() && items.stream().allMatch(item ->
                "boolean".equals(item.type()) || "number".equals(item.type()));
    }

    public Map<String, String> getSceneProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        String startScene = environment.getProperty("hue.start-scene");
        String stopScene = environment.getProperty("hue.stop-scene");
        result.put("hue.start-scene", startScene != null ? startScene : "");
        result.put("hue.stop-scene", stopScene != null ? stopScene : "");
        return result;
    }

    public Map<String, Boolean> getProperties(String mediaType) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        String prefix = "plex." + mediaType + "-";
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

    private String resolveValue(String key) {
        String envValue = environment.getProperty(key);
        return envValue != null ? envValue : "";
    }

    ConfigurationViewItem buildItem(String key, String value) {
        String label = formatLabel(key);
        String type = detectType(value);
        boolean duration = isDuration(key);
        String displayValue = formatDisplayValue(value, type);
        return new ConfigurationViewItem(key, label, value, displayValue, type, duration);
    }

    String detectType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "boolean";
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return "number";
        }
        return "text";
    }

    boolean isDuration(String key) {
        String lower = key.toLowerCase();
        return lower.contains("interval") || lower.contains("timeout") || lower.contains("duration");
    }

    String formatLabel(String key) {
        int dotIndex = key.indexOf('.');
        String suffix = dotIndex >= 0 ? key.substring(dotIndex + 1) : key;
        String[] words = suffix.split("-");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    String formatDisplayValue(String value, String type) {
        if ("number".equals(type)) {
            try {
                long number = Long.parseLong(value);
                return NumberFormat.getNumberInstance().format(number);
            } catch (NumberFormatException e) {
                try {
                    double number = Double.parseDouble(value);
                    return NumberFormat.getNumberInstance().format(number);
                } catch (NumberFormatException e2) {
                    return value;
                }
            }
        }
        return value;
    }
}
