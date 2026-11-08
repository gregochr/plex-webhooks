package gregochr.support;

import gregochr.services.config.SettingChange;
import gregochr.services.config.SettingsRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An in-memory {@link SettingsRepository} that keeps both tables, records its calls and can be told to fail. */
public class FakeSettingsRepository implements SettingsRepository {
    /** One row of the change log, with the time it was written. */
    public record Logged(SettingChange change, Instant at) { }

    public final Map<String, String> settings = Collections.synchronizedMap(new LinkedHashMap<>());
    public final Map<String, Instant> updatedAt = Collections.synchronizedMap(new LinkedHashMap<>());
    public final List<Logged> log = Collections.synchronizedList(new ArrayList<>());
    public final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    public volatile RuntimeException failure;
    public volatile Runnable onLoadAll = () -> { };

    private void failIfTold() {
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
    }

    @Override
    public Map<String, String> loadAll() {
        calls.add("loadAll");
        onLoadAll.run();
        failIfTold();
        return new LinkedHashMap<>(settings);
    }

    @Override
    public void upsert(String key, String value, Instant at) {
        calls.add("upsert " + key);
        failIfTold();
        if (!value.equals(settings.get(key))) {
            settings.put(key, value);
            updatedAt.put(key, at);
        }
    }

    @Override
    public void delete(String key) {
        calls.add("delete " + key);
        failIfTold();
        settings.remove(key);
        updatedAt.remove(key);
    }

    @Override
    public void recordChange(SettingChange change, Instant at) {
        calls.add("record " + change.key());
        failIfTold();
        log.add(new Logged(change, at));
    }

    @Override
    public Set<String> keysWithRecordedValues() {
        calls.add("keysWithRecordedValues");
        failIfTold();
        Set<String> keys = new LinkedHashSet<>();
        synchronized (log) {
            for (Logged row : log) {
                if (row.change().oldValue() != null || row.change().newValue() != null) {
                    keys.add(row.change().key());
                }
            }
        }
        return keys;
    }

    @Override
    public int purgeRecordedValues(Collection<String> keys) {
        calls.add("purge " + keys.stream().sorted().toList());
        failIfTold();
        int before = log.size();
        synchronized (log) {
            log.removeIf(row -> keys.contains(row.change().key())
                    && (row.change().oldValue() != null || row.change().newValue() != null));
        }
        return before - log.size();
    }

    /** The change log without timestamps. */
    public List<SettingChange> changes() {
        synchronized (log) {
            return log.stream().map(Logged::change).toList();
        }
    }
}
