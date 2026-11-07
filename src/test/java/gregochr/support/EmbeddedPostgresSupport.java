package gregochr.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One real PostgreSQL 16 (zonky's embedded binaries) for the whole test JVM, started on first use. Each test class
 * creates its own database in it, so classes cannot see each other's rows. There is no Docker on the owner's Mac,
 * and an in-memory substitute (H2) would not behave like Postgres, so the database tests run on this.
 */
public final class EmbeddedPostgresSupport {
    public static final String USER = "postgres";

    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static EmbeddedPostgres postgres;

    private EmbeddedPostgresSupport() {
    }

    private static synchronized EmbeddedPostgres postgres() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
            }
            EmbeddedPostgres started = postgres;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    started.close();
                } catch (IOException e) {
                    // The JVM is exiting; the embedded server also stops itself when its parent goes away.
                }
            }));
        }
        return postgres;
    }

    /** Creates an empty database and returns its name. */
    public static String createDatabase() {
        String name = "test_db_" + DATABASES.incrementAndGet();
        client("postgres").sql("CREATE DATABASE " + name).update();
        return name;
    }

    public static String jdbcUrl(String database) {
        return postgres().getJdbcUrl(USER, database);
    }

    public static DataSource dataSource(String database) {
        return new DriverManagerDataSource(jdbcUrl(database), USER, "");
    }

    public static JdbcClient client(String database) {
        return JdbcClient.create(dataSource(database));
    }

    /** Runs the application's real Flyway migrations (src/main/resources/db/migration) on the database. */
    public static void migrate(String database) {
        Flyway.configure().dataSource(dataSource(database)).load().migrate();
    }

    /** The server's PostgreSQL version, for example {@code 16.13}. */
    public static String serverVersion() {
        return client("postgres").sql("SHOW server_version").query(String.class).single();
    }
}
