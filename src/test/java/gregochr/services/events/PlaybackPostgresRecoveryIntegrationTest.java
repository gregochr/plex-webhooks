package gregochr.services.events;

import gregochr.services.config.PropertiesRepository;
import gregochr.support.EmbeddedPostgresSupport;
import gregochr.support.SettingsFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The database is rebuilt empty while the app runs (as when utils-pg is recreated): the schema comes back, playback
 * history stores again, and the settings copy is rebuilt from the file.
 */
@DisplayName("Playback Postgres Recovery Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaybackPostgresRecoveryIntegrationTest extends PostgresSpringTestBase {

    private static final String DATABASE = EmbeddedPostgresSupport.createDatabase();
    private static final JdbcClient DB = EmbeddedPostgresSupport.client(DATABASE);

    private static final Path CONFIG = SettingsFiles.create(SettingsFiles.TYPICAL);

    @Autowired
    private PlaybackHistoryWriter writer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
        registry.add(PropertiesRepository.CONFIG_FILE_PROPERTY, CONFIG::toString);
    }

    private static boolean tableExists() {
        return DB.sql("SELECT to_regclass('playback_event') IS NOT NULL").query(Boolean.class).single();
    }

    private static long settingRows() {
        return DB.sql("SELECT count(*) FROM setting").query(Long.class).single();
    }

    @DisplayName("Vanished tables (SQL state 42P01) are recreated without a restart, storing resumes, and the "
            + "settings copy is rebuilt from the file as 'initial' rows")
    @Test
    @Order(1)
    public void tableComesBackAfterBeingDropped() throws Exception {
        await().atMost(20, SECONDS).ignoreExceptions().until(() -> writer.isReady() && tableExists()
                && settingRows() == 4);
        play();
        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event").query(Long.class)
                .single() == 1);

        // What a rebuilt, empty utils-pg looks like: the table and Flyway's history are both gone.
        DB.sql("DROP TABLE playback_event, setting, setting_change, flyway_schema_history").update();
        PlaybackEvent lost = play();
        // The restart waits the normal 30 s before it migrates again.
        await().atMost(90, SECONDS).ignoreExceptions().until(() -> writer.isReady() && tableExists()
                && settingRows() == 4);
        PlaybackEvent after = play();

        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", after.id()).query(Long.class).single() == 1);
        assertEquals(0L, DB.sql("SELECT count(*) FROM playback_event WHERE id = :id").param("id", lost.id())
                .query(Long.class).single(), "the event written while the table was gone is dropped, not replayed");
    }

    @DisplayName("After the rebuild the credential is still not copied, and the change log starts again with "
            + "'initial' rows")
    @Test
    @Order(2)
    public void rebuiltCopyHoldsNoCredentialAndStartsTheLogAgain() {
        await().atMost(90, SECONDS).ignoreExceptions().until(() -> settingRows() == 4);

        assertEquals(0L, DB.sql("SELECT count(*) FROM setting WHERE key = 'hue.application-key' "
                + "OR value LIKE '%FAKE-KEY%'").query(Long.class).single());
        assertEquals(0L, DB.sql("SELECT count(*) FROM setting_change WHERE source <> 'initial' "
                + "OR key = 'hue.application-key' OR new_value LIKE '%FAKE-KEY%'").query(Long.class).single());
    }
}
