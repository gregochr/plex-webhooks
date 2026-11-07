package gregochr.services.events;

import gregochr.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The database is rebuilt empty while the app runs (as when utils-pg is recreated): the schema comes back. */
@DisplayName("Playback Postgres Recovery Integration Tests")
public class PlaybackPostgresRecoveryIntegrationTest extends PostgresSpringTestBase {

    private static final String DATABASE = EmbeddedPostgresSupport.createDatabase();
    private static final JdbcClient DB = EmbeddedPostgresSupport.client(DATABASE);

    @Autowired
    private PlaybackHistoryWriter writer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.jdbcUrl(DATABASE));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresSupport.USER);
    }

    private static boolean tableExists() {
        return DB.sql("SELECT to_regclass('playback_event') IS NOT NULL").query(Boolean.class).single();
    }

    @DisplayName("A vanished table (SQL state 42P01) is recreated without a restart and storing resumes")
    @Test
    public void tableComesBackAfterBeingDropped() throws Exception {
        await().atMost(20, SECONDS).until(() -> writer.isReady() && tableExists());
        play();
        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event").query(Long.class)
                .single() == 1);

        // What a rebuilt, empty utils-pg looks like: the table and Flyway's history are both gone.
        DB.sql("DROP TABLE playback_event, flyway_schema_history").update();
        PlaybackEvent lost = play();
        await().atMost(30, SECONDS).until(() -> writer.isReady() && tableExists());
        PlaybackEvent after = play();

        await().atMost(20, SECONDS).until(() -> DB.sql("SELECT count(*) FROM playback_event WHERE id = :id")
                .param("id", after.id()).query(Long.class).single() == 1);
        assertEquals(0L, DB.sql("SELECT count(*) FROM playback_event WHERE id = :id").param("id", lost.id())
                .query(Long.class).single(), "the event written while the table was gone is dropped, not replayed");
    }
}
