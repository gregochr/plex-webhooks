package gregochr.services.config;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** The settings copy in the database. Every method may throw a Spring data-access exception. */
public interface SettingsRepository {

    /** Every stored setting, key to value. */
    Map<String, String> loadAll();

    /** Inserts the key, or updates its value (and {@code updated_at}) when the value differs. */
    void upsert(String key, String value, Instant at);

    void delete(String key);

    /** Appends one row to the change log. */
    void recordChange(SettingChange change, Instant at);

    /** The keys that have at least one change row holding an old or a new value. */
    Set<String> keysWithRecordedValues();

    /** Deletes the change rows of these keys that hold a value, returning how many. Rows with no value stay. */
    int purgeRecordedValues(Collection<String> keys);
}
