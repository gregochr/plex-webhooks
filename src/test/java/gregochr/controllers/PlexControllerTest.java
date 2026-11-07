package gregochr.controllers;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.commands.PlexOutcome;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackHistory;
import gregochr.services.events.PlaybackService;
import gregochr.services.events.PlexPayloadSanitizer;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** The webhook controller in front of the real pipeline: only the Hue bridge controller and SSE are mocked. */
@DisplayName("Plex Controller Tests")
public class PlexControllerTest {

    private HueSceneController hueSceneController;
    private SseService sseService;
    private ConfigService configService;
    private PlaybackService playbackService;
    private PlexController controller;

    private static String payload(String event, String type) {
        return "{\"event\":\"" + event + "\",\"user\":true,\"Account\":{\"title\":\"alice\"},"
                + "\"Player\":{\"title\":\"Den\",\"publicAddress\":\"203.0.113.42\"},\"Metadata\":{\"type\":\""
                + type + "\",\"title\":\"Good Will Hunting\",\"duration\":7620000,\"year\":1997}}";
    }

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        sseService = mock(SseService.class);
        configService = new ConfigService();
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, true, true, true, false));
        configs.put(PlexMediaType.EPISODE, new PlexMediaTypeConfig(false, false, false, false, false));
        playbackService = new PlaybackService(hueSceneController, configService, new PlexCommandConfig(configs),
                sseService, new PlexPayloadSanitizer(), new PlaybackHistory(), FIXED);
        controller = new PlexController(playbackService);
    }

    private PlaybackEvent broadcastEvent() {
        ArgumentCaptor<PlaybackEvent> event = ArgumentCaptor.forClass(PlaybackEvent.class);
        verify(sseService).broadcast(event.capture());
        return event.getValue();
    }

    @DisplayName("A play event is broadcast with its details, the sanitised payload and the legacy command object")
    @Test
    public void broadcastsEvent() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        controller.plexEvent(payload("media.play", "movie"));

        assertEquals(new PlaybackEvent(1, "2024-03-15T12:00:00Z", "media.play", "movie", "Good Will Hunting", null,
                null, null, 1997, "Den", PlexOutcome.Kind.SCENE_APPLIED, "start-scene", "plex",
                "{\"event\":\"media.play\",\"user\":true,\"Player\":{\"title\":\"Den\"},"
                        + "\"Metadata\":{\"type\":\"movie\",\"title\":\"Good Will Hunting\",\"year\":1997,"
                        + "\"duration\":7620000}}",
                new PlaybackEvent.Command("media.play", "Good Will Hunting", 7620000)), broadcastEvent());
    }

    @DisplayName("A play event whose Metadata has no duration is read as duration 0 and still starts the scene")
    @Test
    public void missingDurationReadsAsZero() {
        controller.plexEvent("{\"event\":\"media.play\",\"Metadata\":{\"title\":\"x\",\"type\":\"movie\"}}");

        assertEquals(0, broadcastEvent().command().duration());
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("A play event whose duration is null is read as duration 0 and still starts the scene")
    @Test
    public void nullDurationReadsAsZero() {
        controller.plexEvent(
                "{\"event\":\"media.play\",\"Metadata\":{\"title\":\"x\",\"duration\":null,\"type\":\"movie\"}}");

        assertEquals(0, broadcastEvent().command().duration());
        verify(hueSceneController).startMovieLightScene();
    }

    @DisplayName("An enabled play event for a movie starts the movie light scene")
    @Test
    public void playMovieStartsScene() {
        controller.plexEvent(payload("media.play", "movie"));

        verify(hueSceneController).startMovieLightScene();
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("An enabled stop event for a movie stops the movie light scene")
    @Test
    public void stopMovieStopsScene() {
        controller.plexEvent(payload("media.stop", "movie"));

        verify(hueSceneController).stopMovieLightScene();
        verifyNoMoreInteractions(hueSceneController);
    }

    @DisplayName("A scrobble for a movie is broadcast as IGNORED_RULE_OFF and does not touch the lights")
    @Test
    public void disabledScrobbleBroadcastsButDoesNotActivate() {
        controller.plexEvent(payload("media.scrobble", "movie"));

        assertEquals(PlexOutcome.Kind.IGNORED_RULE_OFF, broadcastEvent().outcome());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("A play event for an episode is broadcast as IGNORED_RULE_OFF and does not touch the lights")
    @Test
    public void disabledEpisodeDoesNotActivate() {
        controller.plexEvent(payload("media.play", "episode"));

        assertEquals(PlexOutcome.Kind.IGNORED_RULE_OFF, broadcastEvent().outcome());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("With Hue integration off, an enabled play event is broadcast as IGNORED_HUE_DISABLED")
    @Test
    public void integrationOffDoesNotActivate() {
        configService.setHueIntegrationEnabled(false);

        controller.plexEvent(payload("media.play", "movie"));

        assertEquals(PlexOutcome.Kind.IGNORED_HUE_DISABLED, broadcastEvent().outcome());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("An unrecognised event is broadcast under its own name as IGNORED_UNSUPPORTED")
    @Test
    public void unknownEventBroadcastOnly() {
        controller.plexEvent(payload("media.rate", "movie"));

        PlaybackEvent event = broadcastEvent();
        assertEquals("media.rate", event.event());
        assertEquals("", event.command().title());
        assertEquals(PlexOutcome.Kind.IGNORED_UNSUPPORTED, event.outcome());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("An unrecognised event is IGNORED_UNSUPPORTED even with Hue integration off")
    @Test
    public void unknownEventWithIntegrationOff() {
        configService.setHueIntegrationEnabled(false);

        controller.plexEvent(payload("media.rate", "movie"));

        assertEquals(PlexOutcome.Kind.IGNORED_UNSUPPORTED, broadcastEvent().outcome());
    }

    @DisplayName("A payload without Metadata is broadcast under its event name and does not touch the lights")
    @Test
    public void payloadWithoutMetadataBroadcastOnly() {
        controller.plexEvent("{\"event\":\"media.play\"}");

        PlaybackEvent event = broadcastEvent();
        assertEquals("media.play", event.event());
        assertNull(event.title());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("Every recognised event name is mapped to its PlexEvent")
    @Test
    public void everyEventNameMaps() {
        for (PlexEvent plexEvent : PlexEvent.values()) {
            controller.plexEvent(payload(plexEvent.getEvent(), "movie"));
        }

        ArgumentCaptor<PlaybackEvent> events = ArgumentCaptor.forClass(PlaybackEvent.class);
        verify(sseService, times(5)).broadcast(events.capture());
        assertEquals(List.of("media.play", "media.resume", "media.stop", "media.pause", "media.scrobble"),
                events.getAllValues().stream().map(PlaybackEvent::event).toList());
    }

    @DisplayName("A webhook still succeeds when the Hue path throws; the event is recorded as FAILED")
    @Test
    public void hueExceptionStillSucceeds() {
        when(hueSceneController.startMovieLightScene()).thenThrow(new IllegalStateException("bridge down"));

        controller.plexEvent(payload("media.play", "movie"));

        assertEquals(PlexOutcome.Kind.FAILED, broadcastEvent().outcome());
    }

    @DisplayName("A payload that is not JSON raises a JacksonException and broadcasts nothing")
    @Test
    public void invalidJsonRaises() {
        assertThrows(JacksonException.class, () -> controller.plexEvent("not json"));

        verifyNoInteractions(sseService, hueSceneController);
    }
}
