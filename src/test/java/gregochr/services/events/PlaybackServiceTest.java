package gregochr.services.events;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexMediaTypeConfig;
import gregochr.commands.PlexOutcome;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import gregochr.services.plex.dto.PlexPlayerDto;
import gregochr.services.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Playback Service Tests")
public class PlaybackServiceTest {

    private HueSceneController hueSceneController;
    private SseService sseService;
    private ConfigService configService;
    private PlaybackHistory history;
    private PlaybackService service;

    private static final String RAW = "{\"event\":\"media.play\",\"Account\":{\"title\":\"alice\"},"
            + "\"Player\":{\"title\":\"Living Room TV\",\"publicAddress\":\"203.0.113.42\"},"
            + "\"Metadata\":{\"type\":\"episode\",\"title\":\"Cold Open\",\"grandparentTitle\":\"Slow Horses\","
            + "\"parentIndex\":4,\"index\":2,\"year\":2025,\"thumb\":\"/x\"}}";

    @BeforeEach
    public void setUp() {
        hueSceneController = mock(HueSceneController.class);
        sseService = mock(SseService.class);
        configService = new ConfigService();
        history = new PlaybackHistory();
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        configs.put(PlexMediaType.MOVIE, new PlexMediaTypeConfig(true, true, true, true, false));
        configs.put(PlexMediaType.EPISODE, new PlexMediaTypeConfig(true, true, true, true, true));
        service = new PlaybackService(hueSceneController, configService, new PlexCommandConfig(configs),
                sseService, new PlexPayloadSanitizer(), history, FIXED);
    }

    private static PlexPayloadDto episodePayload(String event) {
        return new PlexPayloadDto(event,
                new PlexMetadataDto("Cold Open", 2700000, "episode", "Slow Horses", 4, 2, 2025),
                new PlexPlayerDto("Living Room TV"));
    }

    private static PlexCommand episodeCommand(PlexEvent event) {
        return new PlexCommand(event, "Cold Open", 2700000, PlexMediaType.EPISODE);
    }

    @DisplayName("A scene that is applied is recorded with every field, its scene id and the sanitised payload")
    @Test
    public void recordsAppliedScene() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        assertEquals(new PlaybackEvent(1, "2024-03-15T12:00:00Z", "media.play", "episode", "Cold Open",
                "Slow Horses", 4, 2, 2025, "Living Room TV", PlexOutcome.Kind.SCENE_APPLIED, "start-scene", "plex",
                "{\"event\":\"media.play\",\"Player\":{\"title\":\"Living Room TV\"},"
                        + "\"Metadata\":{\"type\":\"episode\",\"title\":\"Cold Open\","
                        + "\"grandparentTitle\":\"Slow Horses\",\"parentIndex\":4,\"index\":2,\"year\":2025}}",
                new PlaybackEvent.Command("media.play", "Cold Open", 2700000)), event);
    }

    @DisplayName("The event is broadcast after the command ran, then the now-playing state, and both are stored")
    @Test
    public void executesThenRecordsThenBroadcasts() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.of("start-scene"));

        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        InOrder order = Mockito.inOrder(hueSceneController, sseService);
        order.verify(hueSceneController).startMovieLightScene();
        order.verify(sseService).broadcast(event);
        ArgumentCaptor<NowPlaying> now = ArgumentCaptor.forClass(NowPlaying.class);
        order.verify(sseService).broadcastNowPlaying(now.capture());
        assertEquals(NowPlaying.State.PLAYING, now.getValue().state());
        assertEquals("start-scene", now.getValue().sceneId());
        assertEquals(now.getValue(), service.nowPlaying());
        assertEquals(List.of(event), service.recent(10));
    }

    @DisplayName("The broadcast event carries the sanitised payload, never the raw one")
    @Test
    public void broadcastPayloadIsSanitised() {
        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        assertFalse(event.payload().contains("Account"));
        assertFalse(event.payload().contains("203.0.113.42"));
        assertFalse(event.payload().contains("thumb"));
    }

    @DisplayName("Ids count up from 1 and each event gets its own")
    @Test
    public void idsAreMonotonic() {
        PlaybackEvent first = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");
        PlaybackEvent second = service.process(episodeCommand(PlexEvent.STOP), episodePayload("media.stop"), RAW,
                "plex");

        assertEquals(1, first.id());
        assertEquals(2, second.id());
    }

    @DisplayName("The timestamp is the injected clock's instant as an ISO string")
    @Test
    public void timestampComesFromClock() {
        assertEquals("2024-03-15T12:00:00Z",
                service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW, "plex").timestamp());
    }

    @DisplayName("The source is recorded as given")
    @Test
    public void sourceIsRecorded() {
        assertEquals("debug",
                service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW, "debug").source());
    }

    @DisplayName("A failed scene change is recorded as FAILED with no scene id")
    @Test
    public void failedOutcome() {
        when(hueSceneController.startMovieLightScene()).thenReturn(Optional.empty());

        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        assertEquals(PlexOutcome.Kind.FAILED, event.outcome());
        assertNull(event.sceneId());
    }

    @DisplayName("A RuntimeException from the Hue path is recorded as FAILED and is not rethrown")
    @Test
    public void hueExceptionIsRecordedAsFailed() {
        when(hueSceneController.startMovieLightScene()).thenThrow(new IllegalStateException("bridge exploded"));

        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        assertEquals(PlexOutcome.Kind.FAILED, event.outcome());
        verify(sseService).broadcast(event);
        assertEquals(List.of(event), service.recent(10));
    }

    @DisplayName("With Hue integration off the event is recorded as IGNORED_HUE_DISABLED and the lights are untouched")
    @Test
    public void hueDisabled() {
        configService.setHueIntegrationEnabled(false);

        PlaybackEvent event = service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW,
                "plex");

        assertEquals(PlexOutcome.Kind.IGNORED_HUE_DISABLED, event.outcome());
        verify(hueSceneController, never()).startMovieLightScene();
    }

    @DisplayName("A disabled rule is recorded as IGNORED_RULE_OFF")
    @Test
    public void ruleOff() {
        PlexCommand command = new PlexCommand(PlexEvent.SCROBBLE, "Heat", 1, PlexMediaType.MOVIE);

        PlaybackEvent event = service.process(command,
                new PlexPayloadDto("media.scrobble", new PlexMetadataDto("Heat", 1, "movie")), RAW, "plex");

        assertEquals(PlexOutcome.Kind.IGNORED_RULE_OFF, event.outcome());
    }

    @DisplayName("A recognised event for an unsupported media type is recorded as IGNORED_UNSUPPORTED")
    @Test
    public void unsupportedMediaType() {
        PlexCommand command = new PlexCommand("media.play", null);

        PlaybackEvent event = service.process(command,
                new PlexPayloadDto("media.play", new PlexMetadataDto("Gig", 1, "show")), RAW, "plex");

        assertEquals(PlexOutcome.Kind.IGNORED_UNSUPPORTED, event.outcome());
        assertEquals("show", event.mediaType());
        assertEquals(NowPlaying.State.IDLE, service.nowPlaying().state());
    }

    @DisplayName("A recognised event with an unknown media type is recorded as IGNORED_UNKNOWN_TYPE")
    @Test
    public void unknownMediaType() {
        PlexCommand command = new PlexCommand(PlexEvent.PLAY, "x", 0, null);

        PlaybackEvent event = service.process(command,
                new PlexPayloadDto("media.play", new PlexMetadataDto("x", 0, "bogus")), RAW, "debug");

        assertEquals(PlexOutcome.Kind.IGNORED_UNKNOWN_TYPE, event.outcome());
        assertEquals("bogus", event.mediaType());
        assertEquals(NowPlaying.State.IDLE, service.nowPlaying().state());
    }

    @DisplayName("A payload with no Metadata or Player gives an event with those fields empty")
    @Test
    public void payloadWithoutMetadataOrPlayer() {
        PlaybackEvent event = service.process(new PlexCommand("media.play", null),
                new PlexPayloadDto("media.play", null), "{\"event\":\"media.play\"}", "plex");

        assertNull(event.mediaType());
        assertNull(event.title());
        assertNull(event.player());
        assertNull(event.year());
        assertEquals("{\"event\":\"media.play\"}", event.payload());
    }

    @DisplayName("A media event is stored in the history and updates now-playing")
    @Test
    public void mediaEventStored() {
        service.process(episodeCommand(PlexEvent.PAUSE), episodePayload("media.pause"), RAW, "plex");

        assertEquals(1, service.recent(10).size());
        assertEquals(NowPlaying.State.PAUSED, service.nowPlaying().state());
    }

    @DisplayName("A non-media event is broadcast but is not stored and does not touch now-playing")
    @Test
    public void nonMediaEventNotStored() {
        PlaybackEvent event = service.process(new PlexCommand("library.new", PlexMediaType.MOVIE),
                new PlexPayloadDto("library.new", new PlexMetadataDto("Heat", 1, "movie")),
                "{\"event\":\"library.new\"}", "plex");

        verify(sseService).broadcast(event);
        verify(sseService, never()).broadcastNowPlaying(any());
        assertEquals(List.of(), service.recent(10));
        assertEquals(NowPlaying.State.IDLE, service.nowPlaying().state());
    }

    @DisplayName("An event with no name is treated as non-media")
    @Test
    public void nullEventNotStored() {
        service.process(new PlexCommand((String) null, null), new PlexPayloadDto(null, null), "{}", "plex");

        assertEquals(List.of(), service.recent(10));
    }

    @DisplayName("A media event the app does not recognise is stored, with the outcome IGNORED_UNSUPPORTED")
    @Test
    public void unrecognisedMediaEventStored() {
        service.process(new PlexCommand("media.rate", PlexMediaType.MOVIE),
                new PlexPayloadDto("media.rate", new PlexMetadataDto("Heat", 1, "movie")),
                "{\"event\":\"media.rate\"}", "plex");

        assertEquals(PlexOutcome.Kind.IGNORED_UNSUPPORTED, service.recent(10).get(0).outcome());
        verify(sseService).broadcastNowPlaying(service.nowPlaying());
    }

    @DisplayName("Concurrent events are recorded and broadcast in the same order, with unique ids")
    @Test
    public void concurrentEventsKeepOrder() throws Exception {
        int events = 64;
        List<Long> broadcastIds = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            broadcastIds.add(invocation.<PlaybackEvent>getArgument(0).id());
            return null;
        }).when(sseService).broadcast(any(PlaybackEvent.class));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = IntStream.range(0, events).<Future<?>>mapToObj(i -> executor.submit(() -> {
                start.await();
                service.process(episodeCommand(PlexEvent.PLAY), episodePayload("media.play"), RAW, "plex");
                return null;
            })).toList();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<Long> recordedOldestFirst = new ArrayList<>(service.recent(PlaybackHistory.CAPACITY).stream()
                .map(PlaybackEvent::id).toList());
        Collections.reverse(recordedOldestFirst);
        assertEquals(events, broadcastIds.size());
        assertEquals(recordedOldestFirst, broadcastIds);
        assertEquals(IntStream.rangeClosed(1, events).boxed().map(Long::valueOf).toList(), broadcastIds);
        assertEquals(NowPlaying.State.PLAYING, service.nowPlaying().state());
    }
}
