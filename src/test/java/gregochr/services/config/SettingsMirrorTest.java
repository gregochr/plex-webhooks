package gregochr.services.config;

import gregochr.support.FakeSettingsRepository;
import gregochr.support.LogCapture;
import gregochr.support.MutableClock;
import gregochr.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Settings Mirror Tests")
public class SettingsMirrorTest {

    private static final String HUE_KEY = "FAKE-HUE-APPLICATION-KEY-7f3a91";
    private static final String ADMIN_PASSWORD = "FAKE-ADMIN-PASSWORD-55d2e0";
    private static final String DATASOURCE_URL = "jdbc:postgresql://db:5432/cinelight?password=FAKE-DS-PASSWORD-1c8b";

    @TempDir
    Path dir;

    private Path file;
    private FakeSettingsRepository repository;
    private PlatformTransactionManager transactionManager;
    private MutableClock clock;
    private TaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private SettingsMirror mirror;
    private LogCapture log;
    private final AtomicInteger restarts = new AtomicInteger();

    @BeforeEach
    public void setUp() throws IOException {
        file = dir.resolve("application.properties");
        write("# Hue", "hue.hub-address=1.1.1.1", "sse.heartbeat-interval=10000",
                "plex.movie-play-enabled=true",
                "hue.application-key=" + HUE_KEY, "app.admin.password=" + ADMIN_PASSWORD,
                "spring.datasource.url=" + DATASOURCE_URL);
        repository = new FakeSettingsRepository();
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        clock = new MutableClock();
        future = mock(ScheduledFuture.class);
        scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> future);
        mirror = newMirror(new FileSystemResource(file));
        mirror.onSchemaMissing(restarts::incrementAndGet);
        log = new LogCapture(SettingsMirror.class);
    }

    @AfterEach
    public void tearDown() {
        log.close();
    }

    private SettingsMirror newMirror(Resource resource) {
        PropertiesRepository properties = new PropertiesRepository(new MockEnvironment(), resource);
        return new SettingsMirror(repository, properties, new TransactionTemplate(transactionManager), scheduler,
                clock);
    }

    private void write(String... lines) throws IOException {
        Files.write(file, List.of(lines));
    }

    private static PropertiesUpdatedEvent saved(String key, String value) {
        return new PropertiesUpdatedEvent("test", Map.of(key, value));
    }

    private void edit(String from, String to) throws IOException {
        Files.writeString(file, Files.readString(file).replace(from, to));
    }

    @DisplayName("The mirror is named for the log")
    @Test
    public void name() {
        assertEquals("settings mirror", mirror.name());
    }

    @DisplayName("Becoming ready starts the 5-second check, running at once")
    @Test
    public void readyStartsTheCheck() {
        mirror.onDatabaseReady();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT), eq(Duration.ofSeconds(5)));
    }

    @DisplayName("stop cancels the 5-second check")
    @Test
    public void stopCancelsTheCheck() {
        mirror.onDatabaseReady();

        mirror.stop();

        verify(future).cancel(false);
    }

    @DisplayName("Before the database is ready nothing is read or written, even after a save")
    @Test
    public void nothingBeforeReady() {
        mirror.onPropertiesUpdated(saved("sse.heartbeat-interval", "25000"));

        mirror.reconcile();

        assertEquals(List.of(), repository.calls);
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
    }

    @DisplayName("Once ready, the first reconcile copies every non-credential key as 'initial', stamped from the "
            + "injected clock, in one transaction")
    @Test
    public void firstReconcileIsInitial() {
        mirror.onDatabaseReady();

        mirror.reconcile();

        assertEquals(Map.of("hue.hub-address", "1.1.1.1", "sse.heartbeat-interval", "10000",
                "plex.movie-play-enabled", "true"), repository.settings);
        assertEquals(List.of(new SettingChange("hue.hub-address", null, "1.1.1.1", "initial"),
                new SettingChange("plex.movie-play-enabled", null, "true", "initial"),
                new SettingChange("sse.heartbeat-interval", null, "10000", "initial")), repository.changes());
        assertEquals(TestClocks.INSTANT, repository.updatedAt.get("hue.hub-address"));
        assertTrue(repository.log.stream().allMatch(row -> row.at().equals(TestClocks.INSTANT)));
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @DisplayName("Credentials and spring.datasource keys never reach either table: not their keys, not their values")
    @Test
    public void credentialsNeverReachTheDatabase() throws IOException {
        write("hue.hub-address=1.1.1.1", "hue.application-key=" + HUE_KEY, "app.admin.password=" + ADMIN_PASSWORD,
                "spring.datasource.url=" + DATASOURCE_URL, "dirigera.access-token=FAKE-TOKEN-0a1b",
                "foo.api-key=FAKE-APIKEY-2c3d", "x.client-secret=FAKE-SECRET-4e5f",
                "spring.datasource.password=FAKE-DSPW-6a7b");
        mirror.onDatabaseReady();

        mirror.reconcile();
        mirror.onPropertiesUpdated(saved("hue.application-key", "FAKE-NEWKEY-8c9d"));
        edit("1.1.1.1", "2.2.2.2");
        mirror.reconcile();

        String everything = repository.settings + " " + repository.changes();
        for (String forbidden : List.of("hue.application-key", "app.admin.password", "spring.datasource",
                "dirigera.access-token", "foo.api-key", "x.client-secret", HUE_KEY, ADMIN_PASSWORD, "FAKE-DS",
                "FAKE-TOKEN-0a1b", "FAKE-APIKEY-2c3d", "FAKE-SECRET-4e5f", "FAKE-DSPW-6a7b", "FAKE-NEWKEY-8c9d")) {
            assertFalse(everything.contains(forbidden), forbidden + " reached the database: " + everything);
        }
        assertEquals(Map.of("hue.hub-address", "2.2.2.2"), repository.settings);
    }

    @DisplayName("A reconcile with nothing dirty does not touch the database")
    @Test
    public void cleanMeansNoWork() {
        mirror.onDatabaseReady();
        mirror.reconcile();
        repository.calls.clear();

        mirror.reconcile();

        assertEquals(List.of(), repository.calls);
    }

    @DisplayName("A dashboard save is recorded as 'dashboard' with the old and new value; saving the same value "
            + "again records nothing")
    @Test
    public void dashboardSaveThenSameValue() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        clock.advance(Duration.ofMinutes(10));
        edit("sse.heartbeat-interval=10000", "sse.heartbeat-interval=25000");
        mirror.onPropertiesUpdated(saved("sse.heartbeat-interval", "25000"));

        mirror.reconcile();

        assertEquals(new SettingChange("sse.heartbeat-interval", "10000", "25000", "dashboard"),
                repository.changes().get(3));
        assertEquals(4, repository.changes().size());
        assertEquals(TestClocks.INSTANT.plus(Duration.ofMinutes(10)),
                repository.updatedAt.get("sse.heartbeat-interval"));

        mirror.onPropertiesUpdated(saved("sse.heartbeat-interval", "25000"));
        mirror.reconcile();

        assertEquals(4, repository.changes().size());
    }

    @DisplayName("A hand edit is recorded as 'sync' for a changed, an added and a removed key; a second reconcile "
            + "records none")
    @Test
    public void handEditIsSync() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        write("hue.hub-address=9.9.9.9", "plex.movie-play-enabled=true", "brand.new-setting=1");
        mirror.onPropertiesUpdated(saved("plex.movie-play-enabled", "true"));

        mirror.reconcile();

        List<SettingChange> afterInitial = repository.changes().subList(3, repository.changes().size());
        assertEquals(List.of(new SettingChange("brand.new-setting", null, "1", "sync"),
                new SettingChange("hue.hub-address", "1.1.1.1", "9.9.9.9", "sync"),
                new SettingChange("sse.heartbeat-interval", "10000", null, "sync")), afterInitial);
        assertEquals(Map.of("hue.hub-address", "9.9.9.9", "plex.movie-play-enabled", "true", "brand.new-setting", "1"),
                repository.settings);

        mirror.onPropertiesUpdated(saved("plex.movie-play-enabled", "true"));
        mirror.reconcile();

        assertEquals(6, repository.changes().size());
    }

    @DisplayName("Every key of a save made before the database was ready is caught up by the first reconcile")
    @Test
    public void savesBeforeReadyAreCaughtUp() throws IOException {
        edit("sse.heartbeat-interval=10000", "sse.heartbeat-interval=30000");
        mirror.onPropertiesUpdated(saved("sse.heartbeat-interval", "30000"));
        mirror.reconcile();
        assertEquals(Map.of(), repository.settings);

        mirror.onDatabaseReady();
        mirror.reconcile();

        assertEquals("30000", repository.settings.get("sse.heartbeat-interval"));
    }

    @DisplayName("A save that lands during a reconcile is not lost: the next run picks it up as 'dashboard'")
    @Test
    public void saveDuringReconcileIsPickedUpNextRun() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        repository.onLoadAll = () -> {
            repository.onLoadAll = () -> { };
            try {
                edit("hue.hub-address=1.1.1.1", "hue.hub-address=3.3.3.3");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            mirror.onPropertiesUpdated(saved("hue.hub-address", "3.3.3.3"));
        };
        mirror.onPropertiesUpdated(saved("plex.movie-play-enabled", "true"));

        mirror.reconcile();
        assertEquals("1.1.1.1", repository.settings.get("hue.hub-address"));
        mirror.reconcile();

        assertEquals("3.3.3.3", repository.settings.get("hue.hub-address"));
        assertEquals(new SettingChange("hue.hub-address", "1.1.1.1", "3.3.3.3", "dashboard"),
                repository.changes().get(repository.changes().size() - 1));
    }

    @DisplayName("On a database error the reconcile is rolled back, the saved keys are kept and the next run "
            + "records them as 'dashboard'")
    @Test
    public void errorKeepsSavedKeysForTheNextRun() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        edit("sse.heartbeat-interval=10000", "sse.heartbeat-interval=45000");
        mirror.onPropertiesUpdated(saved("sse.heartbeat-interval", "45000"));
        repository.failure = new IllegalStateException("connection reset");

        mirror.reconcile();

        verify(transactionManager).rollback(any());
        assertEquals("10000", repository.settings.get("sse.heartbeat-interval"));
        repository.failure = null;

        mirror.reconcile();

        assertEquals(new SettingChange("sse.heartbeat-interval", "10000", "45000", "dashboard"),
                repository.changes().get(repository.changes().size() - 1));
        assertEquals(0, restarts.get());
    }

    @DisplayName("Repeated database errors log at most one warning a minute, and a later one after the minute")
    @Test
    public void errorWarningsAreRateLimited() {
        mirror.onDatabaseReady();
        repository.failure = new IllegalStateException("connection reset");

        mirror.reconcile();
        mirror.reconcile();
        mirror.reconcile();

        assertEquals(1, log.warnings().size());
        assertEquals("Settings not copied to the database (IllegalStateException: connection reset); it is "
                + "retried every 5 s (0 similar warnings suppressed since the last)", log.warnings().get(0));

        clock.advance(Duration.ofMillis(SettingsMirror.WARN_INTERVAL_MS - 1));
        mirror.reconcile();
        assertEquals(1, log.warnings().size());

        clock.advance(Duration.ofMillis(1));
        mirror.reconcile();
        assertEquals(2, log.warnings().size());
        assertTrue(log.warnings().get(1).endsWith("(3 similar warnings suppressed since the last)"),
                log.warnings().get(1));
    }

    @DisplayName("A missing table (42P01) stops reconciling, asks for the schema to be recreated, and the copy "
            + "catches up once the database is ready again")
    @Test
    public void missingTableRestartsTheMigration() {
        mirror.onDatabaseReady();
        repository.failure = new BadSqlGrammarException("select", "select 1", new SQLException("gone", "42P01"));

        mirror.reconcile();

        assertEquals(1, restarts.get());
        assertEquals(1, log.warnings().size());
        repository.failure = null;
        repository.calls.clear();
        mirror.reconcile();
        assertEquals(List.of(), repository.calls);
        assertEquals(1, restarts.get());

        mirror.onDatabaseReady();
        mirror.reconcile();

        assertEquals("1.1.1.1", repository.settings.get("hue.hub-address"));
    }

    @DisplayName("An unreadable settings file leaves the copy alone (it is not mistaken for an empty file) and is "
            + "retried")
    @Test
    public void unreadableFileDoesNotWipeTheCopy() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("disk gone"));
        SettingsMirror unreadable = newMirror(broken);
        unreadable.onDatabaseReady();

        unreadable.reconcile();

        assertEquals(3, repository.settings.size());
        assertEquals(1, log.warnings().size());
        assertTrue(log.warnings().get(0).startsWith("Settings not copied to the database (UncheckedIOException: "),
                log.warnings().get(0));
    }

    @DisplayName("A key that has become a credential since it was stored is deleted, its recorded values are purged, "
            + "and only a value-free deletion row remains, once")
    @Test
    public void newlySensitiveKeyIsPurged() throws IOException {
        repository.settings.put("foo.api-key", "FAKE-OLD-SECRET");
        repository.log.add(new FakeSettingsRepository.Logged(
                new SettingChange("foo.api-key", null, "FAKE-OLD-SECRET", "initial"), TestClocks.INSTANT));
        repository.log.add(new FakeSettingsRepository.Logged(
                new SettingChange("foo.api-key", "FAKE-OLD-SECRET", "FAKE-NEWER-SECRET", "dashboard"),
                TestClocks.INSTANT));
        repository.log.add(new FakeSettingsRepository.Logged(
                new SettingChange("removed.token", "FAKE-TOKEN-VALUE", null, "sync"), TestClocks.INSTANT));
        write("foo.api-key=FAKE-NEWER-SECRET", "hue.hub-address=1.1.1.1");
        mirror.onDatabaseReady();

        mirror.reconcile();

        assertEquals(Map.of("hue.hub-address", "1.1.1.1"), repository.settings);
        assertEquals(List.of(new SettingChange("foo.api-key", null, null, "sync"),
                new SettingChange("hue.hub-address", null, "1.1.1.1", "sync")), repository.changes());
        assertTrue(repository.calls.contains("purge [foo.api-key, removed.token]"), repository.calls.toString());

        mirror.onPropertiesUpdated(saved("hue.hub-address", "1.1.1.1"));
        repository.calls.clear();
        mirror.reconcile();

        assertEquals(2, repository.changes().size());
        assertEquals(List.of("loadAll", "keysWithRecordedValues"), repository.calls);
    }

    @DisplayName("Keys that are not credentials are never purged, and nothing is purged when nothing leaks")
    @Test
    public void nothingToPurge() {
        mirror.onDatabaseReady();

        mirror.reconcile();

        assertFalse(repository.calls.stream().anyMatch(call -> call.startsWith("purge")), repository.calls.toString());
    }

    @DisplayName("A file with the same key twice is mirrored with the last value")
    @Test
    public void lastDuplicateWins() throws IOException {
        write("hue.hub-address=1.1.1.1", "hue.hub-address=2.2.2.2");
        mirror.onDatabaseReady();

        mirror.reconcile();

        assertEquals(Map.of("hue.hub-address", "2.2.2.2"), repository.settings);
    }

    @DisplayName("Saved keys are only remembered until the reconcile that takes them")
    @Test
    public void savedKeysAreForgottenAfterReconcile() throws IOException {
        mirror.onDatabaseReady();
        mirror.onPropertiesUpdated(new PropertiesUpdatedEvent("test", Map.of("a", "1")));
        mirror.reconcile();
        write("hue.hub-address=1.1.1.1", "plex.movie-play-enabled=true", "sse.heartbeat-interval=10000", "a=2");

        mirror.onPropertiesUpdated(saved("hue.hub-address", "1.1.1.1"));
        mirror.reconcile();

        assertEquals(new SettingChange("a", null, "2", "sync"),
                repository.changes().get(repository.changes().size() - 1));
        assertEquals(Set.of("a", "hue.hub-address", "plex.movie-play-enabled", "sse.heartbeat-interval"),
                repository.settings.keySet());
    }

    @DisplayName("An emptied settings file (or one holding only credentials) does not wipe a non-empty copy: nothing "
            + "is deleted, one rate-limited warning is logged, and nothing is retried until the next save")
    @Test
    public void emptyFileLeavesTheCopyAlone() throws IOException {
        mirror.onDatabaseReady();
        mirror.reconcile();
        int logged = repository.changes().size();
        repository.calls.clear();
        write("hue.application-key=" + HUE_KEY);
        mirror.onPropertiesUpdated(saved("hue.hub-address", "1.1.1.1"));

        mirror.reconcile();
        mirror.onPropertiesUpdated(saved("hue.hub-address", "1.1.1.1"));
        mirror.reconcile();

        assertEquals(3, repository.settings.size());
        assertEquals(logged, repository.changes().size());
        assertFalse(repository.calls.stream().anyMatch(c -> c.startsWith("delete")), repository.calls.toString());
        assertEquals(List.of("the settings file has no entries; the database copy is left as it is (0 similar "
                + "warnings suppressed since the last)"), log.warnings());
        repository.calls.clear();
        mirror.reconcile();
        assertEquals(List.of(), repository.calls);
    }

    @DisplayName("An empty file into an empty table is a plain no-op, with no warning")
    @Test
    public void emptyFileIntoEmptyTableIsQuiet() throws IOException {
        write("# nothing");
        mirror.onDatabaseReady();

        mirror.reconcile();

        assertEquals(Map.of(), repository.settings);
        assertEquals(List.of(), log.warnings());
    }
}
