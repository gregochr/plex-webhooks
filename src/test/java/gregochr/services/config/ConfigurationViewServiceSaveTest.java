package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Save safety against a real file: concurrent saves lose nothing, and a CR or LF never reaches the file. */
@DisplayName("ConfigurationViewService save safety")
public class ConfigurationViewServiceSaveTest {

    private static final int ROUNDS = 300;

    private final List<Map<String, String>> published = Collections.synchronizedList(new ArrayList<>());
    private final ApplicationEventPublisher publisher = event -> published.add(((PropertiesUpdatedEvent) event)
            .getUpdatedProperties());

    private ConfigurationViewService serviceFor(Path file) {
        PropertiesRepository repository = new PropertiesRepository(
                new MockEnvironment().withProperty(PropertiesRepository.CONFIG_FILE_PROPERTY, file.toString()));
        return new ConfigurationViewService(repository, mock(ConfigurationViewFormatter.class), publisher);
    }

    @DisplayName("Two saves at the same moment never lose one another's change")
    @Test
    public void concurrentSavesLoseNothing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("application.properties");
        Files.write(file, List.of("hue.start-scene=0", "hue.stop-scene=0", "sse.heartbeat-interval=1000"));
        ConfigurationViewService service = serviceFor(file);
        CyclicBarrier start = new CyclicBarrier(2);
        CyclicBarrier done = new CyclicBarrier(3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> save(service, start, done, "hue.start-scene"));
            Future<?> b = pool.submit(() -> save(service, start, done, "hue.stop-scene"));
            for (int round = 1; round <= ROUNDS; round++) {
                done.await(30, TimeUnit.SECONDS);
                assertEquals(List.of("hue.start-scene=" + round, "hue.stop-scene=" + round,
                        "sse.heartbeat-interval=1000"), Files.readAllLines(file), "round " + round);
                done.await(30, TimeUnit.SECONDS);
            }
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2 * ROUNDS, published.size());
    }

    private static Void save(ConfigurationViewService service, CyclicBarrier start, CyclicBarrier done, String key)
            throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            start.await(30, TimeUnit.SECONDS);
            service.saveProperties(Map.of(key, String.valueOf(round)));
            done.await(30, TimeUnit.SECONDS);
            done.await(30, TimeUnit.SECONDS);
        }
        return null;
    }

    @DisplayName("A value with a CR or LF is refused with IllegalArgumentException: nothing is written or published")
    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"abc\ndef", "abc\rdef", "abc\r\ndef", "\n", "x\nhue.stop-scene=injected"})
    public void lineBreakInValueIsRefused(String value, @TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.write(file, List.of("hue.start-scene=old", "hue.stop-scene=old"));
        ConfigurationViewService service = serviceFor(file);

        assertThrows(IllegalArgumentException.class, () -> service.saveProperties(Map.of("hue.start-scene", value)));

        assertEquals(List.of("hue.start-scene=old", "hue.stop-scene=old"), Files.readAllLines(file));
        assertEquals(List.of(), published);
    }

    @DisplayName("A key with a CR or LF is refused too")
    @Test
    public void lineBreakInKeyIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.write(file, List.of("hue.start-scene=old"));

        assertThrows(IllegalArgumentException.class,
                () -> serviceFor(file).saveProperties(Map.of("hue.start-scene\nx", "v")));
    }

    @DisplayName("hasLineBreak is true for a CR or LF in any key or value, and false for clean text, null and empty")
    @Test
    public void hasLineBreak() {
        ConfigurationViewService service = serviceFor(Path.of("unused"));
        Map<String, String> withNullValue = new HashMap<>();
        withNullValue.put("k", null);

        assertTrue(service.hasLineBreak(Map.of("k", "a\nb")));
        assertTrue(service.hasLineBreak(Map.of("k", "a\rb")));
        assertTrue(service.hasLineBreak(Map.of("k\n", "v")));
        assertTrue(service.hasLineBreak(Map.of("k\r", "v")));
        assertTrue(service.hasLineBreak(Map.of("k", "\nabc")), "a LF as the very first character");
        assertTrue(service.hasLineBreak(Map.of("k", "\rabc")), "a CR as the very first character");
        assertTrue(service.hasLineBreak(Map.of("\nk", "v")), "a LF as the very first character of a key");
        assertTrue(service.hasLineBreak(Map.of("\rk", "v")), "a CR as the very first character of a key");
        assertFalse(service.hasLineBreak(Map.of("k", "a b\t=c")));
        assertFalse(service.hasLineBreak(withNullValue));
        assertFalse(service.hasLineBreak(Map.of()));
        assertFalse(service.hasLineBreak(Map.of("k", "")));
    }
}
