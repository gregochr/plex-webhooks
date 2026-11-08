package gregochr.controllers;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.commands.PlexOutcome;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.events.NowPlaying;
import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackHistory;
import gregochr.services.events.PlaybackService;
import gregochr.services.events.PlexPayloadSanitizer;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Debug endpoint in front of the real pipeline, so it is checked end to end: only Hue and SSE are mocked. */
@DisplayName("Debug Controller Tests")
public class DebugControllerTest {

    private HueSceneController hueSceneController;
    private SseService sseService;
    private ConfigService configService;
    private PlaybackService playbackService;
    private DebugController debugController;

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        sseService = mock(SseService.class);
        configService = new ConfigService();
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, true, true, true, true));
        configs.put(PlexMediaType.EPISODE, new PlexMediaTypeConfig(false, false, false, false, false));
        playbackService = new PlaybackService(hueSceneController, configService, new PlexCommandConfig(configs),
                sseService, new PlexPayloadSanitizer(), new PlaybackHistory(), FIXED,
                Optional.empty());
        debugController = new DebugController(playbackService);
    }

    private PlaybackEvent broadcastEvent() {
        ArgumentCaptor<PlaybackEvent> event = ArgumentCaptor.forClass(PlaybackEvent.class);
        verify(sseService).broadcast(event.capture());
        return event.getValue();
    }

    @DisplayName("Play triggers the start scene and reports success, the outcome and the event id")
    @Test
    public void testPlayCommand() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        Map<String, Object> result = debugController.triggerCommand("play", "movie", null);

        assertEquals(Map.of("command", "play", "type", "movie", "success", true, "outcome", "SCENE_APPLIED",
                "id", 1_710_504_000_000_000L), result);
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("Stop and pause trigger the stop scene")
    @Test
    public void testStopAndPauseCommands() {
        when(hueSceneController.stopMovieLightScene()).thenReturn(Optional.of("stop-scene"));

        assertTrue((Boolean) debugController.triggerCommand("stop", "movie", null).get("success"));
        assertTrue((Boolean) debugController.triggerCommand("pause", "movie", null).get("success"));
        assertTrue((Boolean) debugController.triggerCommand("scrobble", "movie", null).get("success"));
    }

    @DisplayName("Resume triggers the start scene")
    @Test
    public void testResumeCommand() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        assertTrue((Boolean) debugController.triggerCommand("resume", "movie", null).get("success"));
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("The debug event is broadcast as a debug-sourced event with a synthetic payload of Plex's shape")
    @Test
    public void testEventIsBroadcastAsDebug() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        debugController.triggerCommand("play", "movie", null);

        PlaybackEvent event = broadcastEvent();
        assertEquals("debug", event.source());
        assertEquals("media.play", event.event());
        assertEquals("movie", event.mediaType());
        assertEquals("Debug Play Command Test", event.title());
        assertEquals("CineLight debug", event.player());
        assertEquals("start-scene", event.sceneId());
        assertEquals("{\"event\":\"media.play\",\"Player\":{\"title\":\"CineLight debug\"},"
                + "\"Metadata\":{\"type\":\"movie\",\"title\":\"Debug Play Command Test\",\"duration\":0}}",
                event.payload());
    }

    @DisplayName("Each command has its own default title")
    @Test
    public void testDefaultTitles() {
        for (String command : new String[] {"play", "stop", "pause", "resume", "scrobble"}) {
            debugController.triggerCommand(command, "movie", null);
        }

        ArgumentCaptor<PlaybackEvent> events = ArgumentCaptor.forClass(PlaybackEvent.class);
        verify(sseService, org.mockito.Mockito.times(5)).broadcast(events.capture());
        assertEquals(java.util.List.of("Debug Play Command Test", "Debug Stop Command Test", "Debug Pause Test",
                "Debug Resume Test", "Debug Scrobble Test"),
                events.getAllValues().stream().map(PlaybackEvent::title).toList());
    }

    @DisplayName("A supplied title is used, and stays JSON-escaped in the payload")
    @Test
    public void testCustomTitle() {
        debugController.triggerCommand("play", "movie", "<b>\"Heat\"</b>");

        PlaybackEvent event = broadcastEvent();
        assertEquals("<b>\"Heat\"</b>", event.title());
        assertTrue(event.payload().contains("\"title\":\"<b>\\\"Heat\\\"</b>\""));
    }

    @DisplayName("A blank title falls back to the default")
    @Test
    public void testBlankTitle() {
        debugController.triggerCommand("play", "movie", "   ");

        assertEquals("Debug Play Command Test", broadcastEvent().title());
    }

    @DisplayName("An unknown command is IGNORED_UNSUPPORTED and does not call Hue")
    @Test
    public void testUnknownCommand() {
        Map<String, Object> result = debugController.triggerCommand("unknown", "movie", null);

        assertEquals("unknown", result.get("command"));
        assertFalse((Boolean) result.get("success"));
        assertEquals("IGNORED_UNSUPPORTED", result.get("outcome"));
        verify(hueSceneController, never()).startMovieLightScene();
        verify(hueSceneController, never()).stopMovieLightScene();
    }

    @DisplayName("A rule that is off gives success false and IGNORED_RULE_OFF")
    @Test
    public void testEpisodeRuleOff() {
        Map<String, Object> result = debugController.triggerCommand("play", "episode", null);

        assertEquals("episode", result.get("type"));
        assertFalse((Boolean) result.get("success"));
        assertEquals("IGNORED_RULE_OFF", result.get("outcome"));
    }

    @DisplayName("A bad media type gives IGNORED_UNKNOWN_TYPE, not 'rule off', and leaves now-playing alone")
    @Test
    public void testBadMediaType() {
        Map<String, Object> result = debugController.triggerCommand("play", "bogus", null);

        assertEquals(PlexOutcome.Kind.IGNORED_UNKNOWN_TYPE.name(), result.get("outcome"));
        assertEquals("bogus", result.get("type"));
        assertEquals(NowPlaying.State.IDLE, playbackService.nowPlaying().state());
    }

    @DisplayName("With Hue integration off the command is IGNORED_HUE_DISABLED")
    @Test
    public void testHueDisabled() {
        configService.setHueIntegrationEnabled(false);

        Map<String, Object> result = debugController.triggerCommand("play", "movie", null);

        assertFalse((Boolean) result.get("success"));
        assertEquals("IGNORED_HUE_DISABLED", result.get("outcome"));
        verify(hueSceneController, never()).startMovieLightScene();
    }

    @DisplayName("A debug event goes through the real pipeline: it is stored and updates now-playing")
    @Test
    public void testDebugEventIsRecorded() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        debugController.triggerCommand("play", "movie", null);

        assertEquals(1, playbackService.recent(10).size());
        assertEquals(NowPlaying.State.PLAYING, playbackService.nowPlaying().state());
    }
}
