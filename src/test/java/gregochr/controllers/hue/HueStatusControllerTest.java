package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import gregochr.support.TaskSchedulers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static gregochr.support.TestClocks.FIXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Hue Status Controller Tests")
public class HueStatusControllerTest {

    private static final String HUB_ID = "001788fffe7107c8";
    private static final String APP_KEY = "TestApplicationKey1234";

    private HueStatusController controller;
    private HueBridgeClient hueBridgeClient;
    private ConfigService configService;
    private SseService sseService;
    private ScheduledFuture<Object> scheduledFuture;
    private TaskScheduler scheduler;

    @BeforeEach
    public void setUp() {
        hueBridgeClient = mock(HueBridgeClient.class);
        configService = mock(ConfigService.class);
        sseService = mock(SseService.class);
        scheduledFuture = TaskSchedulers.future();
        scheduler = TaskSchedulers.returning(scheduledFuture, TaskSchedulers.future());
        when(hueBridgeClient.getHubId()).thenReturn(HUB_ID);
        when(hueBridgeClient.getAppKey()).thenReturn(APP_KEY);
        when(hueBridgeClient.testConnection()).thenReturn(false);
        when(configService.isHueIntegrationEnabled()).thenReturn(true);
        controller = new HueStatusController(hueBridgeClient, configService, sseService, 30000, scheduler, FIXED);
        // hubAddress is field-injected with @Value, which a plain unit test does not process
        ReflectionTestUtils.setField(controller, "hubAddress", "192.168.1.100");
    }

    private static Map<String, Object> status(boolean keySet, boolean enabled, boolean connected) {
        return Map.of(
                "hubAddress", "192.168.1.100",
                "hubId", HUB_ID,
                "applicationKeySet", keySet,
                "integrationEnabled", enabled,
                "connected", connected,
                "timestamp", "2024-03-15T12:00:00Z");
    }

    @DisplayName("getStatus reports every field, with the clock's timestamp")
    @Test
    public void testGetStatusReturnsAllFields() {
        assertEquals(status(true, true, false), controller.getStatus());
    }

    @DisplayName("getStatus reports integrationEnabled false when disabled")
    @Test
    public void testGetStatusIntegrationEnabledFalse() {
        when(configService.isHueIntegrationEnabled()).thenReturn(false);

        assertEquals(status(true, false, false), controller.getStatus());
    }

    @DisplayName("getStatus reports connected true when the bridge answers")
    @Test
    public void testGetStatusConnected() {
        when(hueBridgeClient.testConnection()).thenReturn(true);

        assertEquals(status(true, true, true), controller.getStatus());
    }

    @DisplayName("getStatus never contains the application key value")
    @Test
    public void testGetStatusNeverContainsKeyValue() {
        Map<String, Object> status = controller.getStatus();

        assertFalse(status.containsKey("applicationKey"));
        assertFalse(status.containsValue(APP_KEY));
    }

    @DisplayName("getStatus reports the application key as not set when the key is blank")
    @Test
    public void testGetStatusKeyBlank() {
        when(hueBridgeClient.getAppKey()).thenReturn(" ");

        assertEquals(status(false, true, false), controller.getStatus());
    }

    @DisplayName("getStatus reports the application key as not set when the key is empty")
    @Test
    public void testGetStatusKeyEmpty() {
        when(hueBridgeClient.getAppKey()).thenReturn("");

        assertEquals(status(false, true, false), controller.getStatus());
    }

    @DisplayName("getStatus reports the application key as not set when the key is null")
    @Test
    public void testGetStatusKeyNull() {
        when(hueBridgeClient.getAppKey()).thenReturn(null);

        assertEquals(status(false, true, false), controller.getStatus());
    }

    @DisplayName("getStatus reports the application key as set when it is a single character")
    @Test
    public void testGetStatusKeySingleCharacter() {
        when(hueBridgeClient.getAppKey()).thenReturn("k");

        assertEquals(status(true, true, false), controller.getStatus());
    }

    @DisplayName("testConnectionEndpoint returns the hub id and connected false when unreachable")
    @Test
    public void testConnectionEndpointUnreachable() {
        assertEquals(Map.of("connected", false, "hubId", HUB_ID), controller.testConnectionEndpoint());
    }

    @DisplayName("testConnectionEndpoint returns the hub id and connected true when reachable")
    @Test
    public void testConnectionEndpointReachable() {
        when(hueBridgeClient.testConnection()).thenReturn(true);

        assertEquals(Map.of("connected", true, "hubId", HUB_ID), controller.testConnectionEndpoint());
    }

    @DisplayName("broadcastStatus broadcasts the status when a client is connected")
    @Test
    public void testBroadcastStatusWithClients() {
        when(sseService.getClientCount()).thenReturn(1);

        controller.broadcastStatus();

        verify(sseService).broadcastHueStatus(status(true, true, false));
    }

    @DisplayName("broadcastStatus does not call the bridge or broadcast when no client is connected")
    @Test
    public void testBroadcastStatusNoClients() {
        when(sseService.getClientCount()).thenReturn(0);

        controller.broadcastStatus();

        verify(sseService).getClientCount();
        verifyNoMoreInteractions(sseService);
        verify(hueBridgeClient, never()).testConnection();
    }

    @DisplayName("init schedules the status broadcast at the configured interval, first run one interval from now")
    @Test
    public void testInitSchedulesBroadcast() {
        when(sseService.getClientCount()).thenReturn(1);

        controller.init();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(
                task.capture(), eq(Instant.parse("2024-03-15T12:00:30Z")), eq(Duration.ofSeconds(30)));
        task.getValue().run();
        verify(sseService).broadcastHueStatus(status(true, true, false));
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

        controller.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("hue.status-interval", "60000")));

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
