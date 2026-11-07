package gregochr.services;

import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

public class ScheduledTaskManager {
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final String propertyKey;
    private final String taskName;
    private final Runnable task;
    private final Logger logger;
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    public ScheduledTaskManager(TaskScheduler taskScheduler, Clock clock, long intervalMs, String propertyKey,
            String taskName, Runnable task, Logger logger) {
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.intervalMs = intervalMs;
        this.propertyKey = propertyKey;
        this.taskName = taskName;
        this.task = task;
        this.logger = logger;
    }

    public synchronized void start() {
        scheduleTask();
    }

    public synchronized void shutdown() {
        cancelScheduledTask();
    }

    private void scheduleTask() {
        cancelScheduledTask();
        Instant firstRun = clock.instant().plusMillis(intervalMs);
        scheduledTask = taskScheduler.scheduleAtFixedRate(task, firstRun, Duration.ofMillis(intervalMs));
        logger.info("Scheduled {} at interval: {} ms", taskName, intervalMs);
    }

    private void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    public synchronized void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        String value = event.getUpdatedProperties().get(propertyKey);
        if (value != null) {
            this.intervalMs = Long.parseLong(value);
            scheduleTask();
            logger.info("Hot-reloaded {}: {} ms", propertyKey, this.intervalMs);
        }
    }
}
