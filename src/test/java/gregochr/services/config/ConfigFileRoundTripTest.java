package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole path a dashboard save takes in the container: PUT /api/config/properties, through the real controller
 * and ConfigurationViewService, into the file named by {@code cinelight.config-file}, with the hot-reload event
 * published afterwards.
 */
@Tag("integration")
@DisplayName("Settings file round trip")
@SpringBootTest
@AutoConfigureMockMvc
public class ConfigFileRoundTripTest {

    private static final Path CONFIG = createConfigFile();

    private static Path createConfigFile() {
        try {
            Path dir = Files.createTempDirectory("cinelight-config-test");
            dir.toFile().deleteOnExit();
            Path file = dir.resolve("application.properties");
            Files.write(file, List.of("# SSE Configuration", "sse.heartbeat-interval=10000", "debug=true"));
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void pointAtTempFile(DynamicPropertyRegistry registry) {
        registry.add(PropertiesRepository.CONFIG_FILE_PROPERTY, CONFIG::toString);
    }

    /** Records every hot-reload event the application publishes. */
    @TestConfiguration
    static class EventRecorderConfig {
        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    static class EventRecorder {
        final List<Map<String, String>> updates = new CopyOnWriteArrayList<>();

        @EventListener
        void on(PropertiesUpdatedEvent event) {
            updates.add(event.getUpdatedProperties());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRecorder recorder;

    @DisplayName("saving a setting rewrites the configured file, keeps its permissions, and publishes the "
            + "PropertiesUpdatedEvent that hot-reloads the listeners")
    @Test
    public void testSaveWritesFileAndPublishesEvent() throws Exception {
        List<Map<String, String>> before = new ArrayList<>(recorder.updates);

        mockMvc.perform(put("/api/config/properties")
                        .with(httpBasic("admin", "cinelight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sse.heartbeat-interval\":\"25000\"}"))
                .andExpect(status().isOk());

        assertEquals(List.of("# SSE Configuration", "sse.heartbeat-interval=25000", "debug=true"),
                Files.readAllLines(CONFIG));
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(CONFIG)));
        assertEquals(List.of("application.properties"), namesInDirectory());
        List<Map<String, String>> after = new ArrayList<>(recorder.updates);
        assertEquals(before.size() + 1, after.size());
        assertEquals(Map.of("sse.heartbeat-interval", "25000"), after.get(after.size() - 1));
    }

    private static List<String> namesInDirectory() throws IOException {
        try (var children = Files.list(CONFIG.getParent())) {
            return children.map(p -> p.getFileName().toString()).toList();
        }
    }
}
