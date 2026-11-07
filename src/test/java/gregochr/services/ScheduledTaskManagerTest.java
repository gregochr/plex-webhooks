package gregochr.services;

import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.support.TaskSchedulers;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("Scheduled Task Manager Tests")
public class ScheduledTaskManagerTest {

    private static final String KEY = "sse.heartbeat-interval";
    private static final Instant FIRST_RUN_30S = Instant.parse("2024-03-15T12:00:30Z");

    private int runs;
    private final Runnable task = () -> runs++;

    private ScheduledTaskManager manager(TaskScheduler scheduler, long intervalMs) {
        return new ScheduledTaskManager(scheduler, FIXED, intervalMs, KEY, "heartbeat", task,
                LogManager.getLogger(ScheduledTaskManagerTest.class));
    }

    private static PropertiesUpdatedEvent update(String key, String value) {
        return new PropertiesUpdatedEvent("test", Map.of(key, value));
    }

    @DisplayName("start schedules the task at a fixed rate, first run one interval from now")
    @Test
    public void startSchedulesAtFixedRate() {
        TaskScheduler scheduler = TaskSchedulers.returning(TaskSchedulers.future());

        manager(scheduler, 30000).start();

        ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(scheduled.capture(), eq(FIRST_RUN_30S), eq(Duration.ofSeconds(30)));
        scheduled.getValue().run();
        assertEquals(1, runs);
    }

    @DisplayName("A one millisecond interval is scheduled one millisecond from now")
    @Test
    public void smallestIntervalIsScheduled() {
        TaskScheduler scheduler = TaskSchedulers.returning(TaskSchedulers.future());

        manager(scheduler, 1).start();

        verify(scheduler).scheduleAtFixedRate(
                eq(task), eq(Instant.parse("2024-03-15T12:00:00.001Z")), eq(Duration.ofMillis(1)));
    }

    @DisplayName("start does not run the task itself")
    @Test
    public void startDoesNotRunTask() {
        manager(TaskSchedulers.returning(TaskSchedulers.future()), 30000).start();

        assertEquals(0, runs);
    }

    @DisplayName("start twice cancels the first schedule without interrupting a running task")
    @Test
    public void startTwiceCancelsFirstSchedule() {
        ScheduledFuture<Object> first = TaskSchedulers.future();
        ScheduledFuture<Object> second = TaskSchedulers.future();
        ScheduledTaskManager manager = manager(TaskSchedulers.returning(first, second), 30000);

        manager.start();
        manager.start();

        verify(first).cancel(false);
        verify(second, never()).cancel(false);
    }

    @DisplayName("shutdown cancels the schedule without interrupting a running task")
    @Test
    public void shutdownCancelsSchedule() {
        ScheduledFuture<Object> future = TaskSchedulers.future();
        ScheduledTaskManager manager = manager(TaskSchedulers.returning(future), 30000);
        manager.start();

        manager.shutdown();

        verify(future).cancel(false);
    }

    @DisplayName("shutdown before start does nothing")
    @Test
    public void shutdownBeforeStartDoesNothing() {
        TaskScheduler scheduler = TaskSchedulers.returning();

        manager(scheduler, 30000).shutdown();

        verifyNoMoreInteractions(scheduler);
    }

    @DisplayName("shutdown twice cancels the schedule once")
    @Test
    public void shutdownTwiceCancelsOnce() {
        ScheduledFuture<Object> future = TaskSchedulers.future();
        ScheduledTaskManager manager = manager(TaskSchedulers.returning(future), 30000);
        manager.start();

        manager.shutdown();
        manager.shutdown();

        verify(future).cancel(false);
        verifyNoMoreInteractions(future);
    }

    @DisplayName("A matching property update cancels the old schedule and schedules at the new interval")
    @Test
    public void matchingUpdateReschedules() {
        ScheduledFuture<Object> first = TaskSchedulers.future();
        TaskScheduler scheduler = TaskSchedulers.returning(first, TaskSchedulers.future());
        ScheduledTaskManager manager = manager(scheduler, 30000);
        manager.start();

        manager.onPropertiesUpdated(update(KEY, "60000"));

        verify(first).cancel(false);
        verify(scheduler).scheduleAtFixedRate(
                eq(task), eq(Instant.parse("2024-03-15T12:01:00Z")), eq(Duration.ofSeconds(60)));
    }

    @DisplayName("A property update for another key leaves the schedule alone")
    @Test
    public void unrelatedUpdateIgnored() {
        ScheduledFuture<Object> first = TaskSchedulers.future();
        TaskScheduler scheduler = TaskSchedulers.returning(first);
        ScheduledTaskManager manager = manager(scheduler, 30000);
        manager.start();

        manager.onPropertiesUpdated(update("hue.status-interval", "60000"));

        verify(first, never()).cancel(false);
        verify(scheduler).scheduleAtFixedRate(eq(task), eq(FIRST_RUN_30S), eq(Duration.ofSeconds(30)));
        verifyNoMoreInteractions(scheduler);
    }

    @DisplayName("A non-numeric interval is rejected and the existing schedule is kept")
    @Test
    public void nonNumericIntervalRejected() {
        ScheduledFuture<Object> first = TaskSchedulers.future();
        ScheduledTaskManager manager = manager(TaskSchedulers.returning(first), 30000);
        manager.start();

        assertThrows(NumberFormatException.class, () -> manager.onPropertiesUpdated(update(KEY, "soon")));

        verify(first, never()).cancel(false);
    }

    @DisplayName("After a hot reload, a later reload schedules from the interval it set")
    @Test
    public void reloadedIntervalIsUsedForNextSchedule() {
        TaskScheduler scheduler = TaskSchedulers.returning(
                TaskSchedulers.future(), TaskSchedulers.future(), TaskSchedulers.future());
        ScheduledTaskManager manager = manager(scheduler, 30000);
        manager.start();
        manager.onPropertiesUpdated(update(KEY, "60000"));

        manager.start();

        verify(scheduler, times(2)).scheduleAtFixedRate(
                eq(task), eq(Instant.parse("2024-03-15T12:01:00Z")), eq(Duration.ofSeconds(60)));
    }
}
