package gregochr.services.config;

import gregochr.controllers.hue.HueSceneController;
import gregochr.services.events.PlaybackDatabaseLifecycle;
import gregochr.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole path of the settings copy on a real PostgreSQL: the app starts with a settings file that holds
 * credentials, a dashboard save goes through the real controller, and a reconcile copies the file to {@code setting}
 * and logs the difference in {@code setting_change}. Credentials and {@code spring.datasource.*} never arrive.
 */
@Tag("integration")
@Tag("postgres")
@DisplayName("Settings Postgres Integration Tests")
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SettingsPostgresIntegrationTest {

    private static final String HUE_KEY = "FAKE-HUE-APPLICATION-KEY-7f3a91";
    private static final String ADMIN_PASSWORD = "FAKE-ADMIN-PASSWORD-55d2e0";
    private static final String DIRIGERA_TOKEN = "FAKE-DIRIGERA-TOKEN-0a1b2c";
    private static final String API_KEY = "FAKE-API-KEY-2c3d4e";
    private static final String CLIENT_SECRET = "FAKE-CLIENT-SECRET-4e5f6a";
    private static final String DATASOURCE_PASSWORD = "FAKE-DS-PASSWORD-1c8b3d";
    private static final String STALE_SECRET = "FAKE-STALE-SECRET-9d8c7b";
    private static final String LATE_KEY = "FAKE-LATE-API-KEY-6b5a49";

    private static final List<String> FORBIDDEN_KEYS = List.of("hue.application-key", "app.admin.password",
            "dirigera.access-token", "foo.api-key", "x.client-secret", "spring.datasource", "late.api-key");
    private static final List<String> FORBIDDEN_VALUES = List.of(HUE_KEY, ADMIN_PASSWORD, DIRIGERA_TOKEN, API_KEY,
            CLIENT_SECRET, DATASOURCE_PASSWORD, STALE_SECRET, LATE_KEY);

    private static final String DATABASE = EmbeddedPostgresSupport.createDatabase();
    private static final JdbcClient DB = EmbeddedPostgresSupport.client(DATABASE);
    private static final Path CONFIG = createConfigFile();

    private static Path createConfigFile() {
        try {
            Path dir = Files.createTempDirectory("cinelight-settings-copy-test");
            dir.toFile().deleteOnExit();
            Path file = dir.resolve("application.properties");
            Files.write(file, List.of("postcode=DH1 4HU", "hue.hub-address=192.0.2.1",
                    "sse.heartbeat-interval=10000", "hue.status-interval=10000", "hue.scene-catalog-interval=300000",
                    "plex.movie-play-enabled=true",
                    "hue.application-key=" + HUE_KEY, "app.admin.password=" + ADMIN_PASSWORD,
                    "dirigera.access-token=" + DIRIGERA_TOKEN, "foo.api-key=" + API_KEY,
                    "x.client-secret=" + CLIENT_SECRET,
                    "spring.datasource.url=jdbc:postgresql://utils-pg:5432/cinelight?user=cinelight&password="
                            + DATASOURCE_PASSWORD));
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
        registry.add(PropertiesRepository.CONFIG_FILE_PROPERTY, CONFIG::toString);
    }

    @MockitoBean
    HueSceneController hueSceneController;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    /** Waits for a condition on the tables; the tables do not exist until the migration has run. */
    private static void awaitDatabase(Callable<Boolean> condition) {
        await().atMost(30, SECONDS).ignoreExceptions().until(condition);
    }

    private static Map<String, String> settings() {
        Map<String, String> rows = new TreeMap<>();
        DB.sql("SELECT key, value FROM setting").query((rs, i) -> rows.put(rs.getString(1), rs.getString(2))).list();
        return rows;
    }

    private static List<String> changes(String key) {
        return DB.sql("SELECT coalesce(old_value, '(none)') || ' -> ' || coalesce(new_value, '(none)') || ' ['"
                + " || source || ']' FROM setting_change WHERE key = :key ORDER BY id")
                .param("key", key).query(String.class).list();
    }

    /** Fails if any credential's key or value appears anywhere in either table. */
    private static void assertNoCredentialAnywhere() {
        List<String> everything = new ArrayList<>();
        DB.sql("SELECT key || ' ' || value FROM setting").query(String.class).list().forEach(everything::add);
        DB.sql("SELECT key || ' ' || coalesce(old_value, '') || ' ' || coalesce(new_value, '') FROM setting_change")
                .query(String.class).list().forEach(everything::add);
        String all = String.join("\n", everything);
        for (String forbidden : FORBIDDEN_KEYS) {
            assertFalse(all.contains(forbidden), forbidden + " reached the database:\n" + all);
        }
        for (String forbidden : FORBIDDEN_VALUES) {
            assertFalse(all.contains(forbidden), forbidden + " reached the database:\n" + all);
        }
    }

    private void saveInterval(String json) throws Exception {
        mockMvc.perform(put("/api/config/properties").with(httpBasic("admin", "cinelight"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @DisplayName("Once the database is ready the file is copied as 'initial' rows, with no credential and no "
            + "spring.datasource key or value in either table")
    @Test
    @Order(1)
    public void firstReconcileCopiesTheFileWithoutCredentials() {
        awaitDatabase(() -> settings().size() == 6);

        assertEquals(Map.of("hue.hub-address", "192.0.2.1", "hue.scene-catalog-interval", "300000",
                "hue.status-interval", "10000", "plex.movie-play-enabled", "true", "postcode", "DH1 4HU",
                "sse.heartbeat-interval", "10000"), settings());
        assertEquals(List.of("(none) -> 10000 [initial]"), changes("sse.heartbeat-interval"));
        assertEquals(6L, DB.sql("SELECT count(*) FROM setting_change WHERE source = 'initial'")
                .query(Long.class).single());
        assertEquals(6L, DB.sql("SELECT count(*) FROM setting_change").query(Long.class).single());
        assertNoCredentialAnywhere();
    }

    @DisplayName("A dashboard save, a hand edit and a key that is now a credential are all caught by the next "
            + "reconcile: 'dashboard', 'sync', and a purge; credentials still never arrive")
    @Test
    @Order(2)
    public void savesHandEditsAndPurge() throws Exception {
        awaitDatabase(() -> settings().size() == 6);
        // Rows an earlier version could have written: a key that SensitiveKeys now refuses, with recorded values.
        DB.sql("INSERT INTO setting (key, value, updated_at) VALUES ('stale.secret', :v, now())")
                .param("v", STALE_SECRET).update();
        DB.sql("INSERT INTO setting_change (changed_at, key, old_value, new_value, source) "
                + "VALUES (now(), 'stale.secret', NULL, :v, 'initial')").param("v", STALE_SECRET).update();
        // A hand edit: one value changed, one key added, one removed, and a credential added.
        String text = Files.readString(CONFIG);
        Files.writeString(CONFIG, text.replace("hue.hub-address=192.0.2.1", "hue.hub-address=192.0.2.99")
                .replace("postcode=DH1 4HU\n", "") + "new.setting=hello\nlate.api-key=" + LATE_KEY + "\n");

        // A burst of saves: the database ends with the last value, none is lost to an interleaved reconcile.
        for (int interval = 20000; interval < 20010; interval++) {
            saveInterval("{\"hue.status-interval\":\"" + interval + "\"}");
        }
        saveInterval("{\"sse.heartbeat-interval\":\"25000\"}");

        awaitDatabase(() -> "25000".equals(settings().get("sse.heartbeat-interval")));
        assertEquals("20009", settings().get("hue.status-interval"));
        assertEquals(List.of("(none) -> 10000 [initial]", "10000 -> 25000 [dashboard]"),
                changes("sse.heartbeat-interval"));
        assertEquals(List.of("192.0.2.1 -> 192.0.2.99 [sync]"), changes("hue.hub-address").subList(1, 2));
        assertEquals(List.of("(none) -> hello [sync]"), changes("new.setting"));
        assertEquals(List.of("(none) -> DH1 4HU [initial]", "DH1 4HU -> (none) [sync]"), changes("postcode"));
        assertEquals(List.of("(none) -> (none) [sync]"), changes("stale.secret"));
        assertFalse(settings().containsKey("stale.secret"));
        assertNoCredentialAnywhere();
    }

    @DisplayName("Saving a value the file already has records nothing, however many times it is saved")
    @Test
    @Order(3)
    public void savingTheSameValueRecordsNothing() throws Exception {
        awaitDatabase(() -> "25000".equals(settings().get("sse.heartbeat-interval")));
        long before = DB.sql("SELECT count(*) FROM setting_change WHERE key = 'sse.heartbeat-interval'")
                .query(Long.class).single();

        saveInterval("{\"sse.heartbeat-interval\":\"25000\"}");
        saveInterval("{\"sse.heartbeat-interval\":\"25000\",\"hue.scene-catalog-interval\":\"400000\"}");

        awaitDatabase(() -> "400000".equals(settings().get("hue.scene-catalog-interval")));
        assertEquals(before, DB.sql("SELECT count(*) FROM setting_change WHERE key = 'sse.heartbeat-interval'")
                .query(Long.class).single());
        assertEquals(List.of("300000 -> 400000 [dashboard]"),
                changes("hue.scene-catalog-interval").subList(1, 2));
        assertNoCredentialAnywhere();
    }

    @DisplayName("The transaction template is Spring Boot's: one DataSourceTransactionManager on the application's "
            + "DataSource, shared by the template; and the lifecycle runs playback first, settings second")
    @Test
    @Order(4)
    public void transactionWiring() {
        Map<String, PlatformTransactionManager> managers = context.getBeansOfType(PlatformTransactionManager.class);
        Map<String, TransactionTemplate> templates = context.getBeansOfType(TransactionTemplate.class);

        assertEquals(1, managers.size(), managers.keySet().toString());
        assertEquals(1, templates.size(), templates.keySet().toString());
        PlatformTransactionManager manager = managers.values().iterator().next();
        assertTrue(manager instanceof DataSourceTransactionManager);
        assertSame(context.getBean(DataSource.class), ((DataSourceTransactionManager) manager).getDataSource());
        assertSame(manager, templates.values().iterator().next().getTransactionManager());
        // Built by Spring Boot's own auto-configuration (so PlaybackDatabaseConfig does not create a second one).
        String origin = ((ConfigurableApplicationContext) context).getBeanFactory()
                .getBeanDefinition(managers.keySet().iterator().next()).getResourceDescription();
        assertTrue(origin.contains("DataSourceTransactionManagerAutoConfiguration"), origin);
        assertEquals(1, context.getBeansOfType(SettingsMirror.class).size());
        assertEquals(1, context.getBeansOfType(SettingsRepository.class).size());
        assertEquals(1, context.getBeansOfType(PlaybackDatabaseLifecycle.class).size());
    }
}
