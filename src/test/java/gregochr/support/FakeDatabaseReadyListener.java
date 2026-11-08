package gregochr.support;

import gregochr.services.events.DatabaseReadyListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A listener that records its calls into a shared list (to check order) and can be told to fail. */
public class FakeDatabaseReadyListener implements DatabaseReadyListener {
    private final String name;
    private final List<String> calls;
    public final AtomicInteger runs = new AtomicInteger();
    public final AtomicInteger stops = new AtomicInteger();
    public volatile RuntimeException failure;
    public volatile Runnable duringRun = () -> { };
    public volatile Runnable restartMigration;

    public FakeDatabaseReadyListener(String name, List<String> calls) {
        this.name = name;
        this.calls = calls;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void onDatabaseReady() {
        runs.incrementAndGet();
        calls.add(name);
        duringRun.run();
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
    }

    @Override
    public void onSchemaMissing(Runnable restart) {
        this.restartMigration = restart;
    }

    @Override
    public void stop() {
        stops.incrementAndGet();
    }
}
