package gregochr.services.config;

import gregochr.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The real SQL against a real PostgreSQL 16 with the application's real Flyway migrations. */
@Tag("integration")
@Tag("postgres")
@DisplayName("Postgres Settings Repository Tests")
public class PostgresSettingsRepositoryTest {

    private static final Instant T1 = Instant.parse("2024-03-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2024-03-15T13:30:15.123456Z");

    private static String database;
    private static JdbcClient jdbc;
    private static PostgresSettingsRepository repository;

    @BeforeAll
    static void createSchema() {
        database = EmbeddedPostgresSupport.createDatabase();
        EmbeddedPostgresSupport.migrate(database);
        jdbc = EmbeddedPostgresSupport.client(database);
        repository = new PostgresSettingsRepository(jdbc);
    }

    @BeforeEach
    void emptyTables() {
        jdbc.sql("DELETE FROM setting").update();
        jdbc.sql("DELETE FROM setting_change").update();
    }

    private Instant updatedAt(String key) {
        return jdbc.sql("SELECT updated_at FROM setting WHERE key = :key").param("key", key)
                .query(OffsetDateTime.class).single().toInstant();
    }

    @DisplayName("The migration creates both tables with every column and the changed_at index")
    @Test
    public void schema() {
        List<String> settingColumns = jdbc.sql("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'setting' ORDER BY ordinal_position").query(String.class).list();
        List<String> changeColumns = jdbc.sql("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'setting_change' ORDER BY ordinal_position").query(String.class).list();
        List<String> indexes = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE tablename = 'setting_change'")
                .query(String.class).list();

        assertEquals(List.of("key", "value", "updated_at"), settingColumns);
        assertEquals(List.of("id", "changed_at", "key", "old_value", "new_value", "source"), changeColumns);
        assertEquals(1, indexes.stream().filter(i -> i.contains("(changed_at)")).count(), indexes.toString());
    }

    @DisplayName("upsert inserts a new key and loadAll returns every stored setting")
    @Test
    public void upsertInserts() {
        repository.upsert("hue.hub-address", "1.1.1.1", T1);
        repository.upsert("postcode", "DH1 4HU", T1);

        assertEquals(Map.of("hue.hub-address", "1.1.1.1", "postcode", "DH1 4HU"), repository.loadAll());
        assertEquals(T1, updatedAt("hue.hub-address"));
    }

    @DisplayName("upsert of a changed value updates the value and updated_at, keeping one row")
    @Test
    public void upsertUpdates() {
        repository.upsert("hue.hub-address", "1.1.1.1", T1);

        repository.upsert("hue.hub-address", "2.2.2.2", T2);

        assertEquals(Map.of("hue.hub-address", "2.2.2.2"), repository.loadAll());
        assertEquals(T2, updatedAt("hue.hub-address"));
    }

    @DisplayName("upsert of the same value changes nothing, not even updated_at (IS DISTINCT FROM)")
    @Test
    public void upsertSameValueKeepsTimestamp() {
        repository.upsert("hue.hub-address", "1.1.1.1", T1);

        repository.upsert("hue.hub-address", "1.1.1.1", T2);

        assertEquals(T1, updatedAt("hue.hub-address"));
    }

    @DisplayName("An empty value is stored as the empty string, not as NULL")
    @Test
    public void emptyValue() {
        repository.upsert("empty.setting", "", T1);

        assertEquals(Map.of("empty.setting", ""), repository.loadAll());
    }

    @DisplayName("delete removes the key and only that key")
    @Test
    public void deleteRemovesOnlyThatKey() {
        repository.upsert("a", "1", T1);
        repository.upsert("b", "2", T1);

        repository.delete("a");

        assertEquals(Map.of("b", "2"), repository.loadAll());
    }

    @DisplayName("recordChange stores old and new values, NULL for a missing side, the source and the given time")
    @Test
    public void recordChange() {
        repository.recordChange(new SettingChange("a", null, "1", "initial"), T1);
        repository.recordChange(new SettingChange("a", "1", "2", "dashboard"), T2);
        repository.recordChange(new SettingChange("a", "2", null, "sync"), T2);

        List<Map<String, Object>> rows = jdbc.sql("SELECT key, old_value, new_value, source, changed_at "
                + "FROM setting_change ORDER BY id").query().listOfRows();

        assertEquals(3, rows.size());
        assertEquals("a", rows.get(0).get("key"));
        assertEquals(null, rows.get(0).get("old_value"));
        assertEquals("1", rows.get(0).get("new_value"));
        assertEquals("initial", rows.get(0).get("source"));
        assertEquals("1", rows.get(1).get("old_value"));
        assertEquals("2", rows.get(1).get("new_value"));
        assertEquals("dashboard", rows.get(1).get("source"));
        assertEquals(null, rows.get(2).get("new_value"));
        assertEquals(T1, ((Timestamp) rows.get(0).get("changed_at")).toInstant());
        assertEquals(T2, ((Timestamp) rows.get(1).get("changed_at")).toInstant());
    }

    @DisplayName("The database refuses a change source other than initial, dashboard or sync")
    @Test
    public void unknownSourceIsRefused() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.recordChange(new SettingChange("a", null, "1", "mystery"), T1));
    }

    @DisplayName("keysWithRecordedValues lists keys with a value on either side, once each, and not value-free rows")
    @Test
    public void keysWithRecordedValues() {
        repository.recordChange(new SettingChange("has.old", "1", null, "sync"), T1);
        repository.recordChange(new SettingChange("has.new", null, "1", "initial"), T1);
        repository.recordChange(new SettingChange("has.new", "1", "2", "dashboard"), T1);
        repository.recordChange(new SettingChange("value.free", null, null, "sync"), T1);

        assertEquals(Set.of("has.old", "has.new"), repository.keysWithRecordedValues());
    }

    @DisplayName("purgeRecordedValues deletes the named keys' rows that hold a value, and nothing else")
    @Test
    public void purge() {
        repository.recordChange(new SettingChange("secret.token", null, "S1", "initial"), T1);
        repository.recordChange(new SettingChange("secret.token", "S1", "S2", "dashboard"), T1);
        repository.recordChange(new SettingChange("secret.token", "S2", null, "sync"), T1);
        repository.recordChange(new SettingChange("secret.token", null, null, "sync"), T1);
        repository.recordChange(new SettingChange("other", null, "keep", "initial"), T1);
        repository.recordChange(new SettingChange("other.token", null, "T", "initial"), T1);

        int purged = repository.purgeRecordedValues(List.of("secret.token", "other.token"));

        assertEquals(4, purged);
        List<String> left = jdbc.sql("SELECT key || ':' || coalesce(new_value, '-') || ':' || coalesce(old_value, '-') "
                + "FROM setting_change ORDER BY id").query(String.class).list();
        assertEquals(List.of("secret.token:-:-", "other:keep:-"), left);
    }

    @DisplayName("purgeRecordedValues also works for a single key and returns the number of rows deleted")
    @Test
    public void purgeSingleKey() {
        repository.recordChange(new SettingChange("a", null, "1", "initial"), T1);

        assertEquals(1, repository.purgeRecordedValues(Set.of("a")));
    }

    @DisplayName("Inside a transaction a failure rolls back every write, so a half-applied reconcile cannot happen")
    @Test
    public void transactionRollsBack() {
        // The repository and the transaction manager must share ONE DataSource, as they do in the application.
        DataSource dataSource = EmbeddedPostgresSupport.dataSource(database);
        PostgresSettingsRepository inTransaction = new PostgresSettingsRepository(JdbcClient.create(dataSource));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository.upsert("kept", "1", T1);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            inTransaction.upsert("added", "2", T2);
            inTransaction.delete("kept");
            inTransaction.recordChange(new SettingChange("added", null, "2", "sync"), T2);
            throw new IllegalStateException("boom");
        }));

        assertEquals(Map.of("kept", "1"), repository.loadAll());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM setting_change").query(Long.class).single());
    }

    @DisplayName("Querying before the schema exists fails with SQL state 42P01, which the mirror recovers from")
    @Test
    public void missingTableIsSqlState42P01() {
        PostgresSettingsRepository noSchema = new PostgresSettingsRepository(
                EmbeddedPostgresSupport.client(EmbeddedPostgresSupport.createDatabase()));

        BadSqlGrammarException thrown = assertThrows(BadSqlGrammarException.class, noSchema::loadAll);

        SQLException cause = thrown.getSQLException();
        assertEquals("42P01", cause.getSQLState());
    }
}
