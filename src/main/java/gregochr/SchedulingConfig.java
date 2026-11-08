package gregochr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Supplies the {@link TaskScheduler} behind every {@code ScheduledTaskManager}. The pool has one thread for each
 * periodic task (SSE heartbeat, Hue status, Hue scene catalog, DIRIGERA status, and the three database tasks: the
 * database migration retry, the playback-history retention delete and the settings mirror's reconcile check) so a
 * slow hub check or a dead database cannot delay the others.
 */
@Configuration
public class SchedulingConfig {
    static final int POOL_SIZE = 7;
    static final String THREAD_NAME_PREFIX = "cinelight-scheduler-";

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
