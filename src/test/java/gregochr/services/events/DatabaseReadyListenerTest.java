package gregochr.services.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Database Ready Listener Tests")
public class DatabaseReadyListenerTest {

    @DisplayName("A listener that needs neither the restart hook nor a stop can ignore both: the defaults do nothing")
    @Test
    public void defaultsDoNothing() {
        AtomicInteger readyCalls = new AtomicInteger();
        DatabaseReadyListener minimal = new DatabaseReadyListener() {
            @Override
            public String name() {
                return "minimal";
            }

            @Override
            public void onDatabaseReady() {
                readyCalls.incrementAndGet();
            }
        };
        AtomicInteger restarts = new AtomicInteger();

        minimal.onSchemaMissing(restarts::incrementAndGet);
        minimal.stop();

        assertEquals(0, restarts.get());
        assertEquals(0, readyCalls.get());
    }
}
