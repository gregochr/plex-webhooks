package gregochr.services.events;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Everything database-related, created only when {@code spring.datasource.url} is set. Without it nothing here
 * exists (no {@code DataSource}, no writer) and playback history is memory-only, as before.
 *
 * <p>Spring Boot's {@code DataSourceAutoConfiguration} is excluded in {@code application.yml}, so no
 * {@code spring.datasource.hikari.*} key is bound: the pool is built here, in code. The pool never fails startup
 * ({@code initializationFailTimeout} -1) and gives up on a connection after 3 s, so a dead database costs a task
 * thread three seconds, never startup, a webhook or a health check.
 */
@Configuration
@ConditionalOnExpression("!'${spring.datasource.url:}'.isBlank()")
public class PlaybackDatabaseConfig {
    static final long CONNECTION_TIMEOUT_MS = 3000;
    static final long NEVER_FAIL_ON_STARTUP = -1;
    static final int MAX_POOL_SIZE = 4;

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(@Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("cinelight-db");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setInitializationFailTimeout(NEVER_FAIL_ON_STARTUP);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }

    @Bean
    public PlaybackEventRepository playbackEventRepository(JdbcClient jdbcClient) {
        return new PostgresPlaybackEventRepository(jdbcClient);
    }

    @Bean
    public PlaybackHistoryWriter playbackHistoryWriter(PlaybackEventRepository repository, Clock clock) {
        return new PlaybackHistoryWriter(repository, clock);
    }

    @Bean
    public PlaybackDatabaseLifecycle playbackDatabaseLifecycle(Flyway flyway, PlaybackEventRepository repository,
            PlaybackHistory history, PlaybackService playbackService, PlaybackHistoryWriter writer,
            TaskScheduler taskScheduler, Clock clock) {
        return new PlaybackDatabaseLifecycle(flyway::migrate, repository, history, playbackService, writer,
                taskScheduler, clock);
    }
}
