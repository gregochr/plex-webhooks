package gregochr.controllers;

import gregochr.commands.PlexOutcome;
import gregochr.services.events.NowPlaying;
import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Playback Controller Tests")
public class PlaybackControllerTest {

    private PlaybackService playbackService;
    private PlaybackController controller;

    private static PlaybackEvent event(long id) {
        return new PlaybackEvent(id, "2024-03-15T12:00:00Z", "media.play", "episode", "Cold Open", "Slow Horses",
                4, 2, 2025, "Den", PlexOutcome.Kind.SCENE_APPLIED, "scene-1", "plex",
                "{\"event\":\"media.play\"}", new PlaybackEvent.Command("media.play", "Cold Open", 0));
    }

    @BeforeEach
    public void setUp() {
        playbackService = mock(PlaybackService.class);
        controller = new PlaybackController(playbackService);
    }

    @DisplayName("History defaults to the 20 newest events")
    @Test
    public void defaultLimit() {
        controller.history(20);

        verify(playbackService).recent(20);
    }

    @DisplayName("A limit inside 1..100 is passed through, including both ends")
    @Test
    public void limitInRange() {
        controller.history(1);
        controller.history(100);
        controller.history(37);

        verify(playbackService).recent(1);
        verify(playbackService).recent(100);
        verify(playbackService).recent(37);
    }

    @DisplayName("A limit above 100 is clamped to 100, and below 1 to 1")
    @Test
    public void limitIsClamped() {
        controller.history(101);
        controller.history(0);
        controller.history(-5);

        verify(playbackService).recent(100);
        verify(playbackService, times(2)).recent(1);
    }

    @DisplayName("GET /api/playback/history with no limit asks for 20 and serialises the events")
    @Test
    public void historyJson() throws Exception {
        when(playbackService.recent(20)).thenReturn(List.of(event(2), event(1)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/playback/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].timestamp").value("2024-03-15T12:00:00Z"))
                .andExpect(jsonPath("$[0].outcome").value("SCENE_APPLIED"))
                .andExpect(jsonPath("$[0].sceneId").value("scene-1"))
                .andExpect(jsonPath("$[0].grandparentTitle").value("Slow Horses"))
                .andExpect(jsonPath("$[0].parentIndex").value(4))
                .andExpect(jsonPath("$[0].payload.event").value("media.play"))
                .andExpect(jsonPath("$[0].command.title").value("Cold Open"))
                .andExpect(jsonPath("$[0].media").doesNotExist());
    }

    @DisplayName("GET /api/playback/history?limit=3 asks for 3")
    @Test
    public void historyLimitParameter() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/playback/history").param("limit", "3")).andExpect(status().isOk());

        verify(playbackService).recent(3);
    }

    @DisplayName("GET /api/playback/history?limit=abc is a 400")
    @Test
    public void historyBadLimit() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/playback/history").param("limit", "abc")).andExpect(status().isBadRequest());
    }

    @DisplayName("GET /api/playback/now returns the current state with the label as the state")
    @Test
    public void nowJson() throws Exception {
        when(playbackService.nowPlaying()).thenReturn(new NowPlaying(NowPlaying.State.PAUSED,
                "2024-03-15T12:00:00Z", "episode", "Cold Open", "Slow Horses", 4, 2, 2025, "Den", "scene-1"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/playback/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("Paused"))
                .andExpect(jsonPath("$.since").value("2024-03-15T12:00:00Z"))
                .andExpect(jsonPath("$.title").value("Cold Open"))
                .andExpect(jsonPath("$.sceneId").value("scene-1"));
        assertEquals(NowPlaying.State.PAUSED, playbackService.nowPlaying().state());
    }
}
