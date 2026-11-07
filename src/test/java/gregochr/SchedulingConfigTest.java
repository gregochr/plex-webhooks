package gregochr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Clock and Scheduling Config Tests")
public class SchedulingConfigTest {

    private ThreadPoolTaskScheduler scheduler;

    @AfterEach
    public void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private ThreadPoolTaskScheduler scheduler() {
        scheduler = (ThreadPoolTaskScheduler) new SchedulingConfig().taskScheduler();
        scheduler.initialize();
        return scheduler;
    }

    @DisplayName("The task scheduler has one thread for each of the four periodic tasks")
    @Test
    public void schedulerHasFourThreads() {
        assertEquals(4, scheduler().getScheduledThreadPoolExecutor().getCorePoolSize());
    }

    @DisplayName("The task scheduler names its threads cinelight-scheduler-")
    @Test
    public void schedulerThreadNamePrefix() {
        assertEquals("cinelight-scheduler-", scheduler().getThreadNamePrefix());
    }

    @DisplayName("The task scheduler drops cancelled tasks from its queue")
    @Test
    public void schedulerRemovesCancelledTasks() {
        assertTrue(scheduler().getScheduledThreadPoolExecutor().getRemoveOnCancelPolicy());
    }

    @DisplayName("The clock bean runs in the system default zone")
    @Test
    public void clockUsesSystemDefaultZone() {
        Clock clock = new ClockConfig().clock();

        assertEquals(ZoneId.systemDefault(), clock.getZone());
    }
}
