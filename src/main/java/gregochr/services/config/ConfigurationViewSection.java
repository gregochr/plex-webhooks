package gregochr.services.config;

import java.util.List;

public record ConfigurationViewSection(
        String title,
        List<ConfigurationViewItem> items
) {
}
