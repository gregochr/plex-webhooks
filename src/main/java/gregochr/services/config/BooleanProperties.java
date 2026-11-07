package gregochr.services.config;

import java.util.Locale;
import java.util.Set;

/**
 * Single boolean parser for property values, matching Spring's semantics: trimmed, case-insensitive
 * {@code true}, {@code on}, {@code yes} and {@code 1} are true; anything else (including null) is false.
 */
public final class BooleanProperties {

    private static final Set<String> TRUE_VALUES = Set.of("true", "on", "yes", "1");

    private BooleanProperties() {
    }

    public static boolean parse(String value) {
        return value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
