package gregochr.services.events;

import gregochr.controllers.hue.HueSceneController;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application with a Plex webhook driven through MockMvc and only the Hue bridge controller mocked. The
 * subclasses decide what {@code spring.datasource.url} is. Excluded from PIT (it needs the embedded Postgres), but
 * part of the build gate and of JaCoCo.
 */
@Tag("integration")
@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
abstract class PostgresSpringTestBase {
    static final String PLAY = "{\"event\":\"media.play\",\"Metadata\":{\"type\":\"movie\",\"title\":\"Heat\","
            + "\"year\":1995,\"duration\":100},\"Player\":{\"title\":\"Den\"}}";

    @MockitoBean
    HueSceneController hueSceneController;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlaybackService playbackService;

    @Autowired
    PlaybackHistory history;

    /** Sends a Plex {@code media.play} for a movie to the real webhook and returns the event that was recorded. */
    PlaybackEvent play() throws Exception {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));
        mockMvc.perform(multipart("/plex").param("payload", PLAY)).andExpect(status().isOk());
        return playbackService.recent(1).get(0);
    }
}
