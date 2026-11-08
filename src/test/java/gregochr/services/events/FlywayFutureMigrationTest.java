package gregochr.services.events;

import gregochr.support.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A default rollback runs an older image against a database that a newer image migrated. The older image does not
 * know the newer migration (it is "future" to it), and must still start: Flyway has to ignore it, not fail validation.
 * This runs the application's own Flyway bean against a history that holds a migration its code does not have.
 */
@Tag("integration")
@Tag("postgres")
@DisplayName("Flyway Future Migration Tests")
public class FlywayFutureMigrationTest {

    @DisplayName("The application's Flyway ignores an applied migration it has no file for, and still migrates")
    @Test
    public void unknownFutureMigrationIsIgnored() {
        String database = EmbeddedPostgresSupport.createDatabase();
        DataSource dataSource = EmbeddedPostgresSupport.dataSource(database);
        Flyway flyway = new PlaybackDatabaseConfig().flyway(dataSource);
        flyway.migrate();
        JdbcClient db = JdbcClient.create(dataSource);
        // What a newer image's migration V99 leaves behind in Flyway's history.
        db.sql("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, "
                + "installed_by, execution_time, success) VALUES (99, '99', 'from a newer image', 'SQL', "
                + "'V99__from_a_newer_image.sql', 12345, 'cinelight', 5, true)").update();

        Flyway olderImage = new PlaybackDatabaseConfig().flyway(dataSource);

        assertEquals(0, olderImage.migrate().migrationsExecuted);
        List<MigrationInfo> all = Arrays.asList(olderImage.info().all());
        assertTrue(all.stream().anyMatch(m -> m.getVersion().equals(MigrationVersion.fromVersion("99"))
                && m.getState() == MigrationState.FUTURE_SUCCESS), all.toString());
    }

    @DisplayName("Flyway's ignoreMigrationPatterns for the application's bean include *:future")
    @Test
    public void ignorePatternsIncludeFuture() {
        Flyway flyway = new PlaybackDatabaseConfig().flyway(EmbeddedPostgresSupport.dataSource("postgres"));

        assertEquals(List.of("*:future"), Arrays.stream(flyway.getConfiguration().getIgnoreMigrationPatterns())
                .map(Object::toString).toList());
    }
}
