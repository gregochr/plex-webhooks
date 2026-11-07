package gregochr.services.config;

import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ConfigurationViewFormatter {

    private static final Set<String> HIDDEN_KEYS = Set.of("hue.start-scene", "hue.stop-scene");

    public List<ConfigurationViewSection> buildSections(List<PropertySection> raw) {
        List<ConfigurationViewSection> sections = new ArrayList<>();
        for (PropertySection section : raw) {
            List<ConfigurationViewItem> items = new ArrayList<>();
            for (Map.Entry<String, String> entry : section.entries()) {
                if (!HIDDEN_KEYS.contains(entry.getKey())) {
                    items.add(buildItem(entry.getKey(), entry.getValue()));
                }
            }
            if (!items.isEmpty()) {
                sections.add(new ConfigurationViewSection(section.title(), items, isEditable(items)));
            }
        }
        return sections;
    }

    ConfigurationViewItem buildItem(String key, String value) {
        if (SensitiveKeys.isSensitive(key)) {
            boolean isSet = value != null && !value.isBlank();
            return new ConfigurationViewItem(key, formatLabel(key), "", isSet ? "Set" : "Not set", "secret",
                    false, true);
        }
        String label = formatLabel(key);
        String type = detectType(value);
        boolean duration = isDuration(key);
        String displayValue = formatDisplayValue(value, type);
        return new ConfigurationViewItem(key, label, value, displayValue, type, duration, false);
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
        String[] words = suffix.split("[\\-.]");
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

    boolean isEditable(List<ConfigurationViewItem> items) {
        return !items.isEmpty() && items.stream().allMatch(item ->
                "boolean".equals(item.type()) || "number".equals(item.type()));
    }
}
