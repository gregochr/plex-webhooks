package gregochr.services.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Builds the credential-free settings view for the dashboard. */
@Service
public class ConfigSectionsService {

    private final ConfigurationViewService configurationViewService;
    private final boolean adminTabEnabled;

    public ConfigSectionsService(ConfigurationViewService configurationViewService,
                                 @Value("${dashboard.admin-tab-enabled:false}") boolean adminTabEnabled) {
        this.configurationViewService = configurationViewService;
        this.adminTabEnabled = adminTabEnabled;
    }

    /**
     * Builds the current settings view. A credential item is reduced to whether it is set, so its value cannot
     * leak whatever the formatter produced.
     *
     * @return the view, with every section the properties file lists
     */
    public ConfigSectionsView view() {
        Set<String> writableKeys = configurationViewService.getValidPropertyKeys();
        List<ConfigSectionsView.Section> sections = configurationViewService.getConfigurationSections().stream()
                .map(section -> new ConfigSectionsView.Section(section.title(), section.editable(),
                        section.items().stream().map(item -> toItem(item, writableKeys)).toList()))
                .toList();
        return new ConfigSectionsView(adminTabEnabled, sections);
    }

    private static ConfigSectionsView.Item toItem(ConfigurationViewItem item, Set<String> writableKeys) {
        boolean writable = writableKeys.contains(item.key());
        if (SensitiveKeys.isSensitive(item.key())) {
            // The formatter marks a set credential by displayValue "Set"; the raw value is never read here.
            boolean set = "Set".equals(item.displayValue());
            return new ConfigSectionsView.Item(item.key(), item.label(), "secret", false, true, writable,
                    null, null, set);
        }
        return new ConfigSectionsView.Item(item.key(), item.label(), item.type(), item.duration(), false,
                writable, item.value(), item.displayValue(), null);
    }
}
