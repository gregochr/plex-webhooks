package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Settings Diff Tests")
public class SettingsDiffTest {

    private static final Set<String> NONE = Set.of();

    @DisplayName("Equal maps produce no changes, even for a key that was saved")
    @Test
    public void equalMapsNoChanges() {
        Map<String, String> same = Map.of("sse.heartbeat-interval", "10000");

        assertEquals(List.of(), SettingsDiff.compute(same, same, Set.of("sse.heartbeat-interval"), false));
    }

    @DisplayName("Two empty maps produce no changes")
    @Test
    public void bothEmpty() {
        assertEquals(List.of(), SettingsDiff.compute(Map.of(), Map.of(), NONE, true));
    }

    @DisplayName("On a first run every key is an 'initial' addition with no old value, saved or not, in key order")
    @Test
    public void firstRunIsInitial() {
        Map<String, String> file = new LinkedHashMap<>();
        file.put("b.key-two", "2");
        file.put("a.key-one", "1");

        List<SettingChange> changes = SettingsDiff.compute(Map.of(), file, Set.of("a.key-one"), true);

        assertEquals(List.of(new SettingChange("a.key-one", null, "1", "initial"),
                new SettingChange("b.key-two", null, "2", "initial")), changes);
    }

    @DisplayName("A changed value that was in a dashboard save is 'dashboard', with the old and the new value")
    @Test
    public void savedChangeIsDashboard() {
        List<SettingChange> changes = SettingsDiff.compute(Map.of("sse.heartbeat-interval", "10000"),
                Map.of("sse.heartbeat-interval", "25000"), Set.of("sse.heartbeat-interval"), false);

        assertEquals(List.of(new SettingChange("sse.heartbeat-interval", "10000", "25000", "dashboard")), changes);
    }

    @DisplayName("A changed value that was not in a save (a hand edit) is 'sync'")
    @Test
    public void unsavedChangeIsSync() {
        List<SettingChange> changes = SettingsDiff.compute(Map.of("hue.hub-address", "1.1.1.1"),
                Map.of("hue.hub-address", "2.2.2.2"), Set.of("some.other-key"), false);

        assertEquals(List.of(new SettingChange("hue.hub-address", "1.1.1.1", "2.2.2.2", "sync")), changes);
    }

    @DisplayName("A new key after the first run is 'dashboard' when saved and 'sync' when not")
    @Test
    public void newKeyAfterFirstRun() {
        List<SettingChange> changes = SettingsDiff.compute(Map.of("existing", "1"),
                Map.of("existing", "1", "new.saved", "a", "new.edited", "b"), Set.of("new.saved"), false);

        assertEquals(List.of(new SettingChange("new.edited", null, "b", "sync"),
                new SettingChange("new.saved", null, "a", "dashboard")), changes);
    }

    @DisplayName("A key removed from the file is a deletion with its old value and no new one, always 'sync'")
    @Test
    public void removedKeyIsSync() {
        List<SettingChange> changes = SettingsDiff.compute(Map.of("gone.key-one", "x", "kept", "1"),
                Map.of("kept", "1"), Set.of("gone.key-one"), false);

        assertEquals(List.of(new SettingChange("gone.key-one", "x", null, "sync")), changes);
    }

    @DisplayName("Changed, added and removed keys come out together in key order")
    @Test
    public void mixedOrdering() {
        List<SettingChange> changes = SettingsDiff.compute(Map.of("b", "1", "d", "4", "e", "5"),
                Map.of("a", "0", "b", "2", "e", "5"), NONE, false);

        assertEquals(List.of(new SettingChange("a", null, "0", "sync"),
                new SettingChange("b", "1", "2", "sync"),
                new SettingChange("d", "4", null, "sync")), changes);
    }

    @DisplayName("A value that differs only by case or whitespace is a change")
    @Test
    public void valueComparisonIsExact() {
        assertEquals(1, SettingsDiff.compute(Map.of("k", "True"), Map.of("k", "true"), NONE, false).size());
        assertEquals(1, SettingsDiff.compute(Map.of("k", "a"), Map.of("k", "a "), NONE, false).size());
    }

    @DisplayName("Excluded keys in the file are never added, even on a first run")
    @Test
    public void excludedKeysInTheFileAreIgnored() {
        Map<String, String> file = new LinkedHashMap<>();
        file.put("hue.application-key", "FAKE-KEY");
        file.put("app.admin.password", "FAKE-PASSWORD");
        file.put("spring.datasource.url", "jdbc:postgresql://h/db?password=FAKE");
        file.put("hue.hub-address", "1.1.1.1");

        assertEquals(List.of(new SettingChange("hue.hub-address", null, "1.1.1.1", "initial")),
                SettingsDiff.compute(Map.of(), file, NONE, true));
    }

    @DisplayName("A stored row for a key that is now excluded is deleted with NO old value, so the log never copies "
            + "the credential")
    @Test
    public void storedRowOfNowExcludedKeyIsDeletedWithoutOldValue() {
        List<SettingChange> changes = SettingsDiff.compute(
                Map.of("foo.api-key", "FAKE-SECRET", "hue.hub-address", "1.1.1.1"),
                Map.of("foo.api-key", "FAKE-SECRET", "hue.hub-address", "1.1.1.1"), NONE, false);

        assertEquals(List.of(new SettingChange("foo.api-key", null, null, "sync")), changes);
    }

    @DisplayName("Which keys are excluded: credentials by SensitiveKeys, and spring.datasource and everything under it")
    @ParameterizedTest
    @ValueSource(strings = {"hue.application-key", "app.admin.password", "dirigera.access-token", "foo.api-key",
        "x.client-secret", "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
        "spring.datasource.hikari.maximum-pool-size", "Spring.DataSource.Url", "spring.datasource"})
    public void excludedKeys(String key) {
        assertTrue(SettingsDiff.isExcluded(key), key);
    }

    @DisplayName("Ordinary keys, and look-alikes of spring.datasource, are not excluded")
    @ParameterizedTest
    @ValueSource(strings = {"postcode", "hue.hub-address", "hue.start-scene", "plex.movie-play-enabled",
        "sse.heartbeat-interval", "app.admin.username", "spring.datasources.url", "spring.datasource-pool",
        "my.spring.datasource.url", "spring.application.name"})
    public void includedKeys(String key) {
        assertFalse(SettingsDiff.isExcluded(key), key);
    }
}
