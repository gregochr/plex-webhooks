package gregochr.services;

import gregochr.services.config.ConfigurationViewService;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Runs one task at a fixed rate on the shared {@link TaskScheduler}. A task can be hot-reloadable (it has a
 * {@code propertyKey} whose value is its interval), can start straight away instead of one interval from now, and
 * can stop its own schedule once its work is done.
 */
public class ScheduledTaskManager {
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final String propertyKey;
    private final String taskName;
    private final Runnable task;
    private final Logger logger;
    private long initialDelayMs;
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    /** A hot-reloadable task whose first run is one interval from {@link #start()}. */
    public ScheduledTaskManager(TaskScheduler taskScheduler, Clock clock, long intervalMs, String propertyKey,
            String taskName, Runnable task, Logger logger) {
        this(taskScheduler, clock, intervalMs, intervalMs, propertyKey, taskName, task, logger);
    }

    /**
     * A task that is not hot-reloadable ({@link #onPropertiesUpdated} ignores it).
     *
     * @param initialDelayMs how long after {@link #start()} the first run happens; 0 runs it at once
     */
    public ScheduledTaskManager(TaskScheduler taskScheduler, Clock clock, long intervalMs, long initialDelayMs,
            String taskName, Runnable task, Logger logger) {
        this(taskScheduler, clock, intervalMs, initialDelayMs, null, taskName, task, logger);
    }

    private ScheduledTaskManager(TaskScheduler taskScheduler, Clock clock, long intervalMs, long initialDelayMs,
            String propertyKey, String taskName, Runnable task, Logger logger) {
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.intervalMs = intervalMs;
        this.initialDelayMs = initialDelayMs;
        this.propertyKey = propertyKey;
        this.taskName = taskName;
        this.task = task;
        this.logger = logger;
    }

    /**
     * A task that is handed its own manager each time it runs, so it can call {@link #shutdown()} to stop its own
     * schedule when it has finished. Not hot-reloadable.
     */
    public static ScheduledTaskManager selfStopping(TaskScheduler taskScheduler, Clock clock, long intervalMs,
            long initialDelayMs, String taskName, Consumer<ScheduledTaskManager> task, Logger logger) {
        ScheduledTaskManager[] holder = new ScheduledTaskManager[1];
        holder[0] = new ScheduledTaskManager(taskScheduler, clock, intervalMs, initialDelayMs, taskName,
                () -> task.accept(holder[0]), logger);
        return holder[0];
    }

    public synchronized void start() {
        scheduleTask(initialDelayMs);
    }

    /**
     * Starts (or restarts) the schedule with a first run {@code firstRunDelayMs} from now, whatever initial delay
     * the task was built with. Used to wait out a full interval before retrying after a failure.
     */
    public synchronized void startIn(long firstRunDelayMs) {
        scheduleTask(firstRunDelayMs);
    }

    public synchronized void shutdown() {
        cancelScheduledTask();
    }

    private void scheduleTask(long firstRunDelayMs) {
        cancelScheduledTask();
        Instant firstRun = clock.instant().plusMillis(firstRunDelayMs);
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
        if (propertyKey == null) {
            return;
        }
        String value = event.getUpdatedProperties().get(propertyKey);
        if (value != null) {
            Long parsed = ConfigurationViewService.parseIntervalMs(value);
            if (parsed == null) {
                logger.warn("Ignoring invalid value '{}' for {}; keeping {} ms", value, propertyKey, intervalMs);
                return;
            }
            this.intervalMs = parsed;
            this.initialDelayMs = parsed;
            scheduleTask(initialDelayMs);
            logger.info("Hot-reloaded {}: {} ms", propertyKey, this.intervalMs);
        }
    }
}
