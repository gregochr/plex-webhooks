package gregochr.controllers;

import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.sse.SseEventDto;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("Plex Controller Tests")
public class PlexControllerTest {

    private HueSceneController hueSceneController;
    private SseService sseService;
    private ConfigService configService;
    private PlexController controller;

    private static String payload(String event, String type) {
        return "{\"event\":\"" + event + "\",\"user\":true,\"Metadata\":{\"type\":\"" + type
                + "\",\"title\":\"Good Will Hunting\",\"duration\":7620000,\"year\":1997}}";
    }

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        sseService = mock(SseService.class);
        configService = new ConfigService();
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, true, true, true, false));
        configs.put(PlexMediaType.EPISODE, new PlexMediaTypeConfig(false, false, false, false, false));
        controller = new PlexController(hueSceneController, sseService, configService,
                new PlexCommandConfig(configs), FIXED);
    }

    @DisplayName("A play event is broadcast to SSE clients with the command, the raw payload and the clock timestamp")
    @Test
    public void broadcastsEvent() {
        String payload = payload("media.play", "movie");

        controller.plexEvent(payload);

        ArgumentCaptor<SseEventDto> event = ArgumentCaptor.forClass(SseEventDto.class);
        verify(sseService).broadcast(event.capture());
        assertEquals(new SseEventDto(
                new SseEventDto.CommandDto("media.play", "Good Will Hunting", 7620000), payload,
                "2024-03-15T12:00:00Z"), event.getValue());
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

    @DisplayName("A scrobble event for a movie is broadcast but, being disabled, does not touch the lights")
    @Test
    public void disabledScrobbleBroadcastsButDoesNotActivate() {
        controller.plexEvent(payload("media.scrobble", "movie"));

        verify(sseService).broadcast(ArgumentCaptor.forClass(SseEventDto.class).capture());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("A play event for an episode is broadcast but, being disabled, does not touch the lights")
    @Test
    public void disabledEpisodeDoesNotActivate() {
        controller.plexEvent(payload("media.play", "episode"));

        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("With Hue integration off, an enabled play event is broadcast but does not touch the lights")
    @Test
    public void integrationOffDoesNotActivate() {
        configService.setHueIntegrationEnabled(false);

        controller.plexEvent(payload("media.play", "movie"));

        verify(sseService).broadcast(ArgumentCaptor.forClass(SseEventDto.class).capture());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("An unrecognised event is broadcast under its own name and does not touch the lights")
    @Test
    public void unknownEventBroadcastOnly() {
        controller.plexEvent(payload("media.rate", "movie"));

        ArgumentCaptor<SseEventDto> event = ArgumentCaptor.forClass(SseEventDto.class);
        verify(sseService).broadcast(event.capture());
        assertEquals("media.rate", event.getValue().command().event());
        assertEquals("", event.getValue().command().title());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("A payload without Metadata is broadcast under its event name and does not touch the lights")
    @Test
    public void payloadWithoutMetadataBroadcastOnly() {
        controller.plexEvent("{\"event\":\"media.play\"}");

        ArgumentCaptor<SseEventDto> event = ArgumentCaptor.forClass(SseEventDto.class);
        verify(sseService).broadcast(event.capture());
        assertEquals("media.play", event.getValue().command().event());
        verifyNoInteractions(hueSceneController);
    }

    @DisplayName("Every recognised event name is mapped to its PlexEvent")
    @Test
    public void everyEventNameMaps() {
        for (PlexEvent plexEvent : PlexEvent.values()) {
            controller.plexEvent(payload(plexEvent.getEvent(), "movie"));
        }

        ArgumentCaptor<SseEventDto> events = ArgumentCaptor.forClass(SseEventDto.class);
        verify(sseService, times(5)).broadcast(events.capture());
        assertEquals(List.of("media.play", "media.resume", "media.stop", "media.pause", "media.scrobble"),
                events.getAllValues().stream().map(e -> e.command().event()).toList());
    }

    @DisplayName("A payload that is not JSON raises a JacksonException and broadcasts nothing")
    @Test
    public void invalidJsonRaises() {
        assertThrows(JacksonException.class, () -> controller.plexEvent("not json"));

        verifyNoInteractions(sseService, hueSceneController);
    }
}
