package gregochr.services.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The settings view served by {@code GET /api/config/sections}. Credential items carry only whether they are
 * set: their {@code value} and {@code displayValue} are null and, being omitted from the JSON, never reach the
 * browser.
 *
 * @param adminTabEnabled whether the dashboard's admin tab is switched on
 * @param sections        the settings, grouped as in the properties file
 */
public record ConfigSectionsView(boolean adminTabEnabled, List<Section> sections) {

    /**
     * A group of settings.
     *
     * @param title    the section heading
     * @param editable whether the section's items are all plain booleans or numbers
     * @param items    the settings in the section
     */
    public record Section(String title, boolean editable, List<Item> items) {
    }

    /**
     * One setting. {@code set} is present only for a credential; {@code value} and {@code displayValue} are
     * absent for a credential.
     *
     * @param key          the property key
     * @param label        a human-readable name
     * @param type         boolean, number, text or secret
     * @param duration     whether the value is a time interval
     * @param secret       whether the key holds a credential
     * @param writable     whether {@code PUT /api/config/properties} accepts this key
     * @param value        the raw value, null for a credential
     * @param displayValue the formatted value, null for a credential
     * @param set          for a credential, whether it has a value; null otherwise
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String key, String label, String type, boolean duration, boolean secret, boolean writable,
                       String value, String displayValue, Boolean set) {
    }
}
