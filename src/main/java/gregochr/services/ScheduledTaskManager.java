package gregochr.services;

import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledTaskManager {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String propertyKey;
    private final String taskName;
    private final Runnable task;
    private final Logger logger;
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    public ScheduledTaskManager(long intervalMs, String propertyKey, String taskName, Runnable task, Logger logger) {
        this.intervalMs = intervalMs;
        this.propertyKey = propertyKey;
        this.taskName = taskName;
        this.task = task;
        this.logger = logger;
    }

    public void start() {
        scheduleTask();
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void scheduleTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduledTask = scheduler.scheduleAtFixedRate(task, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        logger.info("Scheduled {} at interval: {} ms", taskName, intervalMs);
    }

    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        String value = event.getUpdatedProperties().get(propertyKey);
        if (value != null) {
            this.intervalMs = Long.parseLong(value);
            scheduleTask();
            logger.info("Hot-reloaded {}: {} ms", propertyKey, this.intervalMs);
        }
    }
}
