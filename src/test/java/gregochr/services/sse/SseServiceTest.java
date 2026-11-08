package gregochr.services.sse;

import gregochr.commands.PlexOutcome;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.events.NowPlaying;
import gregochr.services.events.PlaybackEvent;
import gregochr.support.TaskSchedulers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongFunction;

import static gregochr.support.TestClocks.FIXED;
import static gregochr.support.TestClocks.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("SSE Service Tests")
public class SseServiceTest {

    private static final String CHROME_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final String IP = "192.168.1.100";
    private static final String CONNECTED = "event:connected\ndata:{}\n\n";
    private static final String HEARTBEAT = "event:heartbeat\ndata:{}\n\n";

    private final List<RecordingSseEmitter> emitters = new ArrayList<>();
    private final List<Long> requestedTimeouts = new ArrayList<>();
    private boolean failWhenCreated;
    private ScheduledFuture<Object> scheduledFuture;
    private TaskScheduler scheduler;
    private SseService service;

    @BeforeEach
    public void setUp() {
        scheduledFuture = TaskSchedulers.future();
        scheduler = TaskSchedulers.returning(scheduledFuture, TaskSchedulers.future());
        LongFunction<SseEmitter> factory = timeout -> {
            RecordingSseEmitter emitter = new RecordingSseEmitter(timeout);
            if (failWhenCreated) {
                emitter.failingWith(new IOException("client gone"));
            }
            requestedTimeouts.add(timeout);
            emitters.add(emitter);
            return emitter;
        };
        service = new SseService(30000, scheduler, FIXED, factory);
    }

    private RecordingSseEmitter subscribe(String clientId) {
        return (RecordingSseEmitter) service.subscribe(clientId, CHROME_MAC, IP);
    }

    private static PlaybackEvent playEvent() {
        return new PlaybackEvent(7, "2024-03-15T12:00:00Z", "media.play", "movie", "Test Movie", null, null, null,
                1997, "Living Room", PlexOutcome.Kind.SCENE_APPLIED, "scene-1", "plex",
                "{\"event\":\"media.play\"}");
    }

    // ---- subscribe ----

    @DisplayName("Subscribe creates the emitter with a 24 hour timeout")
    @Test
    public void subscribeUsesTwentyFourHourTimeout() {
        subscribe("client-1");

        assertEquals(List.of(86_400_000L), requestedTimeouts);
    }

    @DisplayName("Subscribe returns the emitter it registered")
    @Test
    public void subscribeReturnsRegisteredEmitter() {
        SseEmitter returned = service.subscribe("client-1", CHROME_MAC, IP);

        assertSame(returned, service.getClient("client-1").emitter());
    }

    @DisplayName("Subscribe records the client's details and the connection time from the clock")
    @Test
    public void subscribeRecordsClientDetails() {
        subscribe("client-1");

        SseClient client = service.getClient("client-1");
        assertEquals("client-1", client.clientId());
        assertEquals(CHROME_MAC, client.userAgent());
        assertEquals(IP, client.ipAddress());
        assertEquals(INSTANT, client.connectedAt());
    }

    @DisplayName("Subscribe sends the connected event so the browser's onopen fires")
    @Test
    public void subscribeSendsConnectedEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");

        assertEquals(List.of(CONNECTED), emitter.sent());
    }

    @DisplayName("Subscribe keeps the client registered when the connected event cannot be sent")
    @Test
    public void subscribeKeepsClientWhenConnectedEventFails() {
        failWhenCreated = true;

        subscribe("client-1");

        assertEquals(1, service.getClientCount());
    }

    @DisplayName("Subscribe counts distinct clients")
    @Test
    public void subscribeCountsDistinctClients() {
        assertEquals(0, service.getClientCount());

        subscribe("client-1");
        assertEquals(1, service.getClientCount());

        subscribe("client-2");
        assertEquals(2, service.getClientCount());
    }

    @DisplayName("Subscribing again with the same client id completes the old emitter and keeps one client")
    @Test
    public void resubscribeCompletesOldEmitter() {
        RecordingSseEmitter old = subscribe("client-1");
        RecordingSseEmitter replacement = subscribe("client-1");

        assertEquals(1, old.completeCalls());
        assertEquals(0, replacement.completeCalls());
        assertEquals(1, service.getClientCount());
        assertSame(replacement, service.getClient("client-1").emitter());
    }

    @DisplayName("Subscribing a new client id does not complete other clients' emitters")
    @Test
    public void subscribeDoesNotCompleteOthers() {
        RecordingSseEmitter first = subscribe("client-1");

        subscribe("client-2");

        assertEquals(0, first.completeCalls());
    }

    // ---- emitter lifecycle callbacks ----

    @DisplayName("Emitter completion removes the client")
    @Test
    public void completionRemovesClient() {
        subscribe("client-1").fireCompletion();

        assertEquals(0, service.getClientCount());
    }

    @DisplayName("Emitter timeout removes the client")
    @Test
    public void timeoutRemovesClient() {
        subscribe("client-1").fireTimeout();

        assertEquals(0, service.getClientCount());
    }

    @DisplayName("Emitter error removes the client")
    @Test
    public void errorRemovesClient() {
        subscribe("client-1").fireError(new IOException("broken pipe"));

        assertEquals(0, service.getClientCount());
    }

    @DisplayName("A callback from a replaced emitter does not remove the reconnected client")
    @Test
    public void staleCallbackDoesNotRemoveReconnectedClient() {
        RecordingSseEmitter stale = subscribe("client-1");
        RecordingSseEmitter current = subscribe("client-1");

        stale.fireCompletion();

        assertEquals(1, service.getClientCount());
        assertSame(current, service.getClient("client-1").emitter());
    }

    @DisplayName("A callback removes only its own client")
    @Test
    public void callbackRemovesOnlyItsOwnClient() {
        RecordingSseEmitter first = subscribe("client-1");
        subscribe("client-2");

        first.fireTimeout();

        assertNull(service.getClient("client-1"));
        assertNotNull(service.getClient("client-2"));
    }

    @DisplayName("removeIfSameEmitter ignores an unknown client id")
    @Test
    public void removeIfSameEmitterUnknownClient() {
        RecordingSseEmitter known = subscribe("client-1");

        service.removeIfSameEmitter("client-9", known, "test");

        assertEquals(1, service.getClientCount());
    }

    // ---- broadcasts ----

    @DisplayName("broadcast sends the event as a message event: outcome and raw JSON payload")
    @Test
    public void broadcastSendsMessageEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");

        service.broadcast(playEvent());

        assertEquals(List.of(CONNECTED, "event:message\ndata:{\"id\":7,\"timestamp\":\"2024-03-15T12:00:00Z\","
                + "\"event\":\"media.play\",\"mediaType\":\"movie\",\"title\":\"Test Movie\","
                + "\"grandparentTitle\":null,\"parentIndex\":null,\"index\":null,\"year\":1997,"
                + "\"player\":\"Living Room\",\"outcome\":\"SCENE_APPLIED\",\"sceneId\":\"scene-1\","
                + "\"source\":\"plex\",\"payload\":{\"event\":\"media.play\"}}\n\n"),
                emitter.sent());
    }

    @DisplayName("broadcastNowPlaying sends a now-playing event with the state as its label")
    @Test
    public void broadcastNowPlayingSendsNowPlayingEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");
        NowPlaying nowPlaying = new NowPlaying(NowPlaying.State.PAUSED, "2024-03-15T12:00:00Z", "episode", "Pilot",
                "Slow Horses", 4, 2, 2025, "TV", "scene-2");

        service.broadcastNowPlaying(nowPlaying);

        assertEquals(List.of(CONNECTED, "event:now-playing\ndata:{\"state\":\"Paused\","
                + "\"since\":\"2024-03-15T12:00:00Z\",\"mediaType\":\"episode\",\"title\":\"Pilot\","
                + "\"grandparentTitle\":\"Slow Horses\",\"parentIndex\":4,\"index\":2,\"year\":2025,"
                + "\"player\":\"TV\",\"sceneId\":\"scene-2\"}\n\n"), emitter.sent());
    }

    @DisplayName("broadcastConfig sends a config event")
    @Test
    public void broadcastConfigSendsConfigEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");

        service.broadcastConfig(Map.of("hueIntegrationEnabled", true));

        assertEquals(List.of(CONNECTED, "event:config\ndata:{\"hueIntegrationEnabled\":true}\n\n"), emitter.sent());
    }

    @DisplayName("broadcastHueStatus sends a hue-status event")
    @Test
    public void broadcastHueStatusSendsHueStatusEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");

        service.broadcastHueStatus(Map.of("connected", true));

        assertEquals(List.of(CONNECTED, "event:hue-status\ndata:{\"connected\":true}\n\n"), emitter.sent());
    }

    @DisplayName("broadcastDirigeraStatus sends a dirigera-status event")
    @Test
    public void broadcastDirigeraStatusSendsDirigeraStatusEvent() {
        RecordingSseEmitter emitter = subscribe("client-1");

        service.broadcastDirigeraStatus(Map.of("reachable", false));

        assertEquals(List.of(CONNECTED, "event:dirigera-status\ndata:{\"reachable\":false}\n\n"), emitter.sent());
    }

    @DisplayName("A broadcast reaches every connected client")
    @Test
    public void broadcastReachesEveryClient() {
        RecordingSseEmitter first = subscribe("client-1");
        RecordingSseEmitter second = subscribe("client-2");

        service.broadcastConfig(Map.of("hueIntegrationEnabled", false));

        String expected = "event:config\ndata:{\"hueIntegrationEnabled\":false}\n\n";
        assertEquals(List.of(CONNECTED, expected), first.sent());
        assertEquals(List.of(CONNECTED, expected), second.sent());
    }

    @DisplayName("A broadcast with no clients connected sends nothing and registers nobody")
    @Test
    public void broadcastWithNoClients() {
        service.broadcast(playEvent());

        assertEquals(0, service.getClientCount());
    }

    @DisplayName("A client whose send fails is removed and the others still receive the broadcast")
    @Test
    public void failedSendRemovesOnlyThatClient() {
        RecordingSseEmitter broken = subscribe("client-1");
        RecordingSseEmitter healthy = subscribe("client-2");
        broken.failingWith(new IOException("client gone"));

        service.broadcastConfig(Map.of("hueIntegrationEnabled", true));

        assertNull(service.getClient("client-1"));
        assertEquals(List.of(CONNECTED, "event:config\ndata:{\"hueIntegrationEnabled\":true}\n\n"),
                healthy.sent());
    }

    @DisplayName("A payload that cannot be serialised sends nothing and keeps every client")
    @Test
    public void unserialisablePayloadSendsNothing() {
        RecordingSseEmitter emitter = subscribe("client-1");

        service.broadcastConfig(new Object());

        assertEquals(List.of(CONNECTED), emitter.sent());
        assertEquals(1, service.getClientCount());
    }

    // ---- heartbeat ----

    @DisplayName("Heartbeat sends a heartbeat event to every client")
    @Test
    public void heartbeatSendsToEveryClient() {
        RecordingSseEmitter first = subscribe("client-1");
        RecordingSseEmitter second = subscribe("client-2");

        service.heartbeat();

        assertEquals(List.of(CONNECTED, HEARTBEAT), first.sent());
        assertEquals(List.of(CONNECTED, HEARTBEAT), second.sent());
    }

    @DisplayName("Heartbeat with no clients does nothing")
    @Test
    public void heartbeatWithNoClients() {
        service.heartbeat();

        assertEquals(0, service.getClientCount());
    }

    @DisplayName("A client whose heartbeat fails is removed and the others still receive it")
    @Test
    public void failedHeartbeatRemovesOnlyThatClient() {
        RecordingSseEmitter broken = subscribe("client-1");
        RecordingSseEmitter healthy = subscribe("client-2");
        broken.failingWith(new IOException("client gone"));

        service.heartbeat();

        assertNull(service.getClient("client-1"));
        assertEquals(List.of(CONNECTED, HEARTBEAT), healthy.sent());
    }

    // ---- scheduling ----

    @DisplayName("init schedules the heartbeat at the configured interval, first run one interval from now")
    @Test
    public void initSchedulesHeartbeat() {
        service.init();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(
                task.capture(), eq(Instant.parse("2024-03-15T12:00:30Z")), eq(Duration.ofSeconds(30)));
        RecordingSseEmitter emitter = subscribe("client-1");
        task.getValue().run();
        assertEquals(List.of(CONNECTED, HEARTBEAT), emitter.sent());
    }

    @DisplayName("destroy cancels the heartbeat")
    @Test
    public void destroyCancelsHeartbeat() {
        service.init();

        service.destroy();

        verify(scheduledFuture).cancel(false);
    }

    @DisplayName("A heartbeat interval update reschedules the heartbeat at the new interval")
    @Test
    public void intervalUpdateReschedulesHeartbeat() {
        service.init();

        service.onPropertiesUpdated(
                new PropertiesUpdatedEvent(this, Map.of("sse.heartbeat-interval", "60000")));

        verify(scheduledFuture).cancel(false);
        verify(scheduler).scheduleAtFixedRate(
                ArgumentCaptor.forClass(Runnable.class).capture(),
                eq(Instant.parse("2024-03-15T12:01:00Z")), eq(Duration.ofSeconds(60)));
    }

    @DisplayName("An update to an unrelated key leaves the heartbeat schedule alone")
    @Test
    public void unrelatedUpdateLeavesHeartbeatAlone() {
        service.init();

        service.onPropertiesUpdated(
                new PropertiesUpdatedEvent(this, Map.of("hue.start-scene", "some-scene")));

        verify(scheduledFuture, never()).cancel(false);
        verify(scheduler).scheduleAtFixedRate(
                ArgumentCaptor.forClass(Runnable.class).capture(),
                eq(Instant.parse("2024-03-15T12:00:30Z")), eq(Duration.ofSeconds(30)));
        verifyNoMoreInteractions(scheduler);
    }
}
