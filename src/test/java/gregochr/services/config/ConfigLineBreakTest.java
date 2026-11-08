package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every {@code PUT /api/config/**} handler that writes the settings file refuses a CR or LF with 400, so a value can
 * never inject a line into the file. {@code PUT /hue-scene} used to accept any {@code sceneId}.
 */
@Tag("integration")
@DisplayName("Config API line-break rejection")
@SpringBootTest
@AutoConfigureMockMvc
public class ConfigLineBreakTest {

    private static final List<String> ORIGINAL = List.of("hue.start-scene=old-start", "hue.stop-scene=old-stop",
            "sse.heartbeat-interval=10000", "plex.movie-play-enabled=true");
    private static final Path CONFIG = createConfigFile();

    private static Path createConfigFile() {
        try {
            Path dir = Files.createTempDirectory("cinelight-linebreak-test");
            dir.toFile().deleteOnExit();
            Path file = dir.resolve("application.properties");
            Files.write(file, ORIGINAL);
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

    @Autowired
    private MockMvc mockMvc;

    private void putAndExpect400(String url, String json) throws Exception {
        mockMvc.perform(put(url).with(httpBasic("admin", "cinelight")).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isBadRequest());
        assertEquals(ORIGINAL, Files.readAllLines(CONFIG), "the file must be untouched");
    }

    @DisplayName("PUT hue-scene with a LF, a CR or a CRLF in the scene id is 400 and writes nothing")
    @Test
    public void hueSceneRejectsLineBreaks() throws Exception {
        putAndExpect400("/api/config/hue-scene", "{\"key\":\"hue.start-scene\",\"sceneId\":\"abc\\ninjected=1\"}");
        putAndExpect400("/api/config/hue-scene", "{\"key\":\"hue.start-scene\",\"sceneId\":\"abc\\rx\"}");
        putAndExpect400("/api/config/hue-scene", "{\"key\":\"hue.start-scene\",\"sceneId\":\"abc\\r\\nx\"}");
    }

    @DisplayName("PUT properties with a line break in an interval is 400 and writes nothing")
    @Test
    public void propertiesRejectsLineBreaks() throws Exception {
        putAndExpect400("/api/config/properties", "{\"sse.heartbeat-interval\":\"20000\\nhue.stop-scene=x\"}");
        putAndExpect400("/api/config/properties", "{\"sse.heartbeat-interval\":\"20000\\r\"}");
    }

    @DisplayName("PUT plex-commands with a line break in a key is 400 and writes nothing")
    @Test
    public void plexCommandsRejectsLineBreaksInKeys() throws Exception {
        putAndExpect400("/api/config/plex-commands/movie",
                "{\"plex.movie-play-enabled\\nhue.stop-scene=x\":true}");
        putAndExpect400("/api/config/plex-commands/movie", "{\"plex.movie-play-enabled\\r\":true}");
    }
}
