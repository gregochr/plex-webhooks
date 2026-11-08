package gregochr.services.config;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * SQL only: the {@code setting} and {@code setting_change} tables (see {@code V2__settings.sql}). Every timestamp is
 * passed in from the application's {@code Clock}, never taken from the database's {@code now()}. All the decisions
 * live in {@link SettingsDiff} and {@link SettingsMirror}.
 */
public class PostgresSettingsRepository implements SettingsRepository {

    private static final String UPSERT = """
            INSERT INTO setting (key, value, updated_at) VALUES (:key, :value, :at)
            ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
            WHERE setting.value IS DISTINCT FROM excluded.value""";

    private static final String RECORD = """
            INSERT INTO setting_change (changed_at, key, old_value, new_value, source)
            VALUES (:at, :key, :oldValue, :newValue, :source)""";

    private static final String HOLDING_VALUES =
            "SELECT DISTINCT key FROM setting_change WHERE old_value IS NOT NULL OR new_value IS NOT NULL";

    private static final String PURGE = """
            DELETE FROM setting_change
            WHERE key IN (:keys) AND (old_value IS NOT NULL OR new_value IS NOT NULL)""";

    private final JdbcClient jdbc;

    public PostgresSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> loadAll() {
        Map<String, String> rows = new LinkedHashMap<>();
        jdbc.sql("SELECT key, value FROM setting ORDER BY key")
                .query((rs, rowNum) -> rows.put(rs.getString("key"), rs.getString("value")))
                .list();
        return rows;
    }

    @Override
    public void upsert(String key, String value, Instant at) {
        jdbc.sql(UPSERT).param("key", key).param("value", value).param("at", utc(at)).update();
    }

    @Override
    public void delete(String key) {
        jdbc.sql("DELETE FROM setting WHERE key = :key").param("key", key).update();
    }

    @Override
    public void recordChange(SettingChange change, Instant at) {
        jdbc.sql(RECORD)
                .param("at", utc(at))
                .param("key", change.key())
                .param("oldValue", change.oldValue(), Types.VARCHAR)
                .param("newValue", change.newValue(), Types.VARCHAR)
                .param("source", change.source())
                .update();
    }

    @Override
    public Set<String> keysWithRecordedValues() {
        return new LinkedHashSet<>(jdbc.sql(HOLDING_VALUES).query(String.class).list());
    }

    @Override
    public int purgeRecordedValues(Collection<String> keys) {
        return jdbc.sql(PURGE).param("keys", keys).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
