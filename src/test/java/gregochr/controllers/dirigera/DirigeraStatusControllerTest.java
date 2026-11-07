package gregochr.controllers.dirigera;

import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import gregochr.support.TaskSchedulers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("DIRIGERA Status Controller Tests")
public class DirigeraStatusControllerTest {

    private static final String HUB = "192.168.1.34";

    /** A socket that records where it was asked to connect, whether it was closed, and can refuse to connect. */
    private static class RecordingSocket extends Socket {
        private SocketAddress endpoint;
        private int timeoutMs = -1;
        private boolean closed;
        private final IOException failure;

        RecordingSocket(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            this.endpoint = endpoint;
            this.timeoutMs = timeout;
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private final List<RecordingSocket> sockets = new ArrayList<>();
    private IOException connectFailure;
    private SseService sseService;
    private ScheduledFuture<Object> scheduledFuture;
    private TaskScheduler scheduler;
    private DirigeraStatusController controller;

    @BeforeEach
    public void setUp() {
        sseService = mock(SseService.class);
        scheduledFuture = TaskSchedulers.future();
        scheduler = TaskSchedulers.returning(scheduledFuture, TaskSchedulers.future());
        controller = new DirigeraStatusController(HUB, sseService, 30000, scheduler, FIXED, () -> {
            RecordingSocket socket = new RecordingSocket(connectFailure);
            sockets.add(socket);
            return socket;
        });
    }

    // ---- connection check ----

    @DisplayName("testConnection connects to the hub on port 8443 with a 3 second timeout")
    @Test
    public void testConnectionTargetsHubPort() {
        controller.testConnection();

        assertEquals(1, sockets.size());
        assertEquals(new InetSocketAddress(HUB, 8443), sockets.get(0).endpoint);
        assertEquals(3000, sockets.get(0).timeoutMs);
    }

    @DisplayName("testConnection returns true when the connection succeeds")
    @Test
    public void testConnectionSucceeds() {
        assertTrue(controller.testConnection());
    }

    @DisplayName("testConnection closes the socket after a successful connection")
    @Test
    public void testConnectionClosesSocketOnSuccess() {
        controller.testConnection();

        assertTrue(sockets.get(0).closed);
    }

    @DisplayName("testConnection returns false when the connection fails")
    @Test
    public void testConnectionFails() {
        connectFailure = new IOException("Connection refused");

        assertFalse(controller.testConnection());
    }

    @DisplayName("testConnection closes the socket after a failed connection")
    @Test
    public void testConnectionClosesSocketOnFailure() {
        connectFailure = new IOException("Connection refused");

        controller.testConnection();

        assertTrue(sockets.get(0).closed);
    }

    // ---- status ----

    @DisplayName("getStatus reports the hub address, reachability and the clock's timestamp")
    @Test
    public void testGetStatusReachable() {
        Map<String, Object> status = controller.getStatus();

        assertEquals(Map.of("hubAddress", HUB, "reachable", true, "timestamp", "2024-03-15T12:00:00Z"), status);
    }

    @DisplayName("getStatus reports reachable false when the hub cannot be reached")
    @Test
    public void testGetStatusUnreachable() {
        connectFailure = new IOException("Connection refused");

        Map<String, Object> status = controller.getStatus();

        assertEquals(Map.of("hubAddress", HUB, "reachable", false, "timestamp", "2024-03-15T12:00:00Z"), status);
    }

    // ---- broadcast ----

    @DisplayName("broadcastStatus broadcasts the status when a client is connected")
    @Test
    public void testBroadcastStatusWithClients() {
        when(sseService.getClientCount()).thenReturn(1);

        controller.broadcastStatus();

        verify(sseService).broadcastDirigeraStatus(
                Map.of("hubAddress", HUB, "reachable", true, "timestamp", "2024-03-15T12:00:00Z"));
    }

    @DisplayName("broadcastStatus does not touch the network or broadcast when no client is connected")
    @Test
    public void testBroadcastStatusNoClients() {
        when(sseService.getClientCount()).thenReturn(0);

        controller.broadcastStatus();

        verify(sseService).getClientCount();
        verifyNoMoreInteractions(sseService);
        assertEquals(0, sockets.size());
    }

    // ---- scheduling ----

    @DisplayName("init schedules the status broadcast at the configured interval, first run one interval from now")
    @Test
    public void testInitSchedulesBroadcast() {
        when(sseService.getClientCount()).thenReturn(1);

        controller.init();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(
                task.capture(), eq(Instant.parse("2024-03-15T12:00:30Z")), eq(Duration.ofSeconds(30)));
        task.getValue().run();
        verify(sseService).broadcastDirigeraStatus(
                Map.of("hubAddress", HUB, "reachable", true, "timestamp", "2024-03-15T12:00:00Z"));
    }

    @DisplayName("destroy cancels the status broadcast")
    @Test
    public void testDestroyCancelsBroadcast() {
        controller.init();

        controller.destroy();

        verify(scheduledFuture).cancel(false);
    }

    @DisplayName("A status interval update reschedules the broadcast at the new interval")
    @Test
    public void testOnPropertiesUpdatedReschedules() {
        controller.init();

        controller.onPropertiesUpdated(
                new PropertiesUpdatedEvent(this, Map.of("dirigera.status-interval", "60000")));

        verify(scheduledFuture).cancel(false);
        verify(scheduler).scheduleAtFixedRate(
                ArgumentCaptor.forClass(Runnable.class).capture(),
                eq(Instant.parse("2024-03-15T12:01:00Z")), eq(Duration.ofSeconds(60)));
    }

    @DisplayName("An update to an unrelated key leaves the schedule alone")
    @Test
    public void testOnPropertiesUpdatedIgnoresUnrelatedKeys() {
        controller.init();

        controller.onPropertiesUpdated(
                new PropertiesUpdatedEvent(this, Map.of("sse.heartbeat-interval", "60000")));

        verify(scheduledFuture, never()).cancel(false);
        verify(scheduler).scheduleAtFixedRate(
                ArgumentCaptor.forClass(Runnable.class).capture(),
                eq(Instant.parse("2024-03-15T12:00:30Z")), eq(Duration.ofSeconds(30)));
        verifyNoMoreInteractions(scheduler);
    }
}
