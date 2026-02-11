package gregochr.services.config;

public record ConfigurationViewItem(
        String key,
        String label,
        String value,
        String displayValue,
        String type,
        boolean duration
) {
}
