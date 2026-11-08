package gregochr.services.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The reconcile's logic, pure: given what the database holds and what the settings file says, which changes make the
 * database equal the file, and how is each labelled. It also decides which keys never reach the database.
 */
public final class SettingsDiff {
    private static final String DATASOURCE = "spring.datasource";

    private SettingsDiff() {
    }

    /**
     * True for a key that must never be stored: a credential ({@link SensitiveKeys}) or any
     * {@code spring.datasource.*} key, whatever its name, because the URL can carry a password.
     */
    public static boolean isExcluded(String key) {
        if (SensitiveKeys.isSensitive(key)) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals(DATASOURCE) || lower.startsWith(DATASOURCE + ".");
    }

    /**
     * The changes that make {@code dbRows} equal {@code fileEntries} without its excluded keys, in key order.
     * A key new to the database is {@code initial} on a first run, else {@code dashboard} if it was saved since the
     * last reconcile, else {@code sync}; a changed value is {@code dashboard} or {@code sync}; a removed key is
     * {@code sync}. A database row for an excluded key is deleted with no old value, so a credential is never copied
     * into the log.
     *
     * @param dbRows what the {@code setting} table holds
     * @param fileEntries the settings file's entries, including excluded keys
     * @param savedKeys keys in a dashboard save since the last reconcile
     * @param firstRun true when the table was empty
     */
    public static List<SettingChange> compute(Map<String, String> dbRows, Map<String, String> fileEntries,
            Set<String> savedKeys, boolean firstRun) {
        Map<String, String> wanted = new TreeMap<>();
        fileEntries.forEach((key, value) -> {
            if (!isExcluded(key)) {
                wanted.put(key, value);
            }
        });
        List<SettingChange> changes = new ArrayList<>();
        wanted.forEach((key, value) -> {
            String stored = dbRows.get(key);
            if (stored == null) {
                changes.add(new SettingChange(key, null, value, label(key, savedKeys, firstRun)));
            } else if (!stored.equals(value)) {
                changes.add(new SettingChange(key, stored, value, label(key, savedKeys, false)));
            }
        });
        for (String key : new TreeSet<>(dbRows.keySet())) {
            if (!wanted.containsKey(key)) {
                String oldValue = isExcluded(key) ? null : dbRows.get(key);
                changes.add(new SettingChange(key, oldValue, null, SettingChange.SYNC));
            }
        }
        changes.sort(Comparator.comparing(SettingChange::key));
        return changes;
    }

    private static String label(String key, Set<String> savedKeys, boolean firstRun) {
        if (firstRun) {
            return SettingChange.INITIAL;
        }
        return savedKeys.contains(key) ? SettingChange.DASHBOARD : SettingChange.SYNC;
    }
}
