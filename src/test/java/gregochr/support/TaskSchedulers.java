package gregochr.support;

import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds TaskScheduler mocks whose scheduling calls hand back the given futures, one per call, in order. */
public final class TaskSchedulers {

    private TaskSchedulers() {
    }

    public static TaskScheduler returning(ScheduledFuture<?>... futures) {
        AtomicInteger next = new AtomicInteger();
        return Mockito.mock(TaskScheduler.class, invocation -> futures[next.getAndIncrement()]);
    }

    @SuppressWarnings("unchecked")
    public static ScheduledFuture<Object> future() {
        return Mockito.mock(ScheduledFuture.class);
    }
}
