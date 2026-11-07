package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.support.LogCapture;
import gregochr.support.TaskSchedulers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static gregochr.support.TestClocks.FIXED;
import static gregochr.support.TestClocks.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Hue Scene Catalog Tests")
public class HueSceneCatalogTest {

    private HueBridgeClient hueBridgeClient;
    private ConfigService configService;
    private HueSceneController hueSceneController;
    private ScheduledFuture<Object> intervalFuture;
    private TaskScheduler scheduler;
    private HueSceneCatalog catalog;

    private static String fixture(String name) throws IOException {
        try (InputStream in = HueSceneCatalogTest.class.getResourceAsStream("/hue/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    public void setUp() {
        hueBridgeClient = mock(HueBridgeClient.class);
        configService = new ConfigService();
        hueSceneController = mock(HueSceneController.class);
        when(hueSceneController.getStartScene()).thenReturn("scene-movie");
        when(hueSceneController.getStopScene()).thenReturn("scene-bright");
        intervalFuture = TaskSchedulers.future();
        scheduler = TaskSchedulers.returning(intervalFuture, TaskSchedulers.future(), TaskSchedulers.future());
        catalog = new HueSceneCatalog(hueBridgeClient, configService, hueSceneController, 300_000L, scheduler,
                FIXED);
    }

    private void refreshWith(String fixtureName) throws IOException {
        when(hueBridgeClient.getResources()).thenReturn(fixture(fixtureName));
        catalog.refresh();
    }

    @DisplayName("A normal bridge document gives each scene its name, room and mean level")
    @Test
    public void normalDocument() throws Exception {
        refreshWith("resources-normal.json");

        assertEquals(List.of(
                new HueSceneSummary("scene-movie", "Movie Night", "room-living", "Living Room", "room", 23),
                new HueSceneSummary("scene-bright", "Bright", "room-living", "Living Room", "room", 100)),
                catalog.view().scenes());
    }

    @DisplayName("A light switched off counts as 0 in the mean; an off light with brightness data is still 0")
    @Test
    public void lightOffCountsAsZero() throws Exception {
        refreshWith("resources-light-off.json");

        assertEquals(List.of(
                new HueSceneSummary("scene-half-off", "Half off", "room-den", "Den", "room", 40),
                new HueSceneSummary("scene-all-off", "Everything off", "room-den", "Den", "room", 0)),
                catalog.view().scenes());
    }

    @DisplayName("A zone scene reports the zone's type and name, and the level is rounded")
    @Test
    public void zoneScene() throws Exception {
        refreshWith("resources-zone.json");

        HueSceneSummary zone = catalog.view().scenes().get(0);
        assertEquals(new HueSceneSummary("scene-zone", "Evening", "zone-downstairs", "Downstairs", "zone", 34),
                zone);
    }

    @DisplayName("A scene whose room is not in the document has no group name, and no actions gives a null level")
    @Test
    public void orphanAndGrouplessScenes() throws Exception {
        refreshWith("resources-zone.json");

        assertEquals(new HueSceneSummary("scene-orphan", "Orphan", "room-missing", null, "room", null),
                catalog.view().scenes().get(1));
        assertEquals(new HueSceneSummary("scene-no-group", "No group", null, null, null, null),
                catalog.view().scenes().get(2));
    }

    @DisplayName("A scene whose actions carry no brightness or on/off data has a null level, never an invented one")
    @Test
    public void noDimmingGivesNullLevel() throws Exception {
        refreshWith("resources-no-dimming.json");

        assertNull(catalog.view().scenes().get(0).level());
        assertEquals("Sunset", catalog.view().scenes().get(0).name());
    }

    @DisplayName("The view carries the configured start and stop scenes with their catalog entries")
    @Test
    public void viewSelectsStartAndStop() throws Exception {
        refreshWith("resources-normal.json");

        HueSceneCatalog.View view = catalog.view();

        assertEquals("scene-movie", view.start().id());
        assertEquals("Movie Night", view.start().summary().name());
        assertEquals("scene-bright", view.stop().id());
        assertEquals(100, view.stop().summary().level());
    }

    @DisplayName("A configured scene that is not in the catalog has a null summary, and the bridge is not called")
    @Test
    public void unknownSceneHasNullSummaryAndNoBridgeCall() {
        when(hueSceneController.getStartScene()).thenReturn("scene-unknown");

        HueSceneCatalog.Selection start = catalog.view().start();

        assertEquals("scene-unknown", start.id());
        assertNull(start.summary());
        verifyNoInteractions(hueBridgeClient);
    }

    @DisplayName("Reading the view never calls the bridge")
    @Test
    public void viewDoesNotCallBridge() {
        catalog.view();

        verifyNoInteractions(hueBridgeClient);
    }

    @DisplayName("Before the first refresh the catalog is empty")
    @Test
    public void emptyBeforeFirstRefresh() {
        assertEquals(List.of(), catalog.view().scenes());
    }

    @DisplayName("Malformed JSON keeps the last good catalog and logs a warning")
    @Test
    public void malformedJsonKeepsLastGood() throws Exception {
        refreshWith("resources-normal.json");

        try (LogCapture log = new LogCapture(HueSceneCatalog.class)) {
            refreshWith("resources-malformed.json");

            assertEquals(2, catalog.view().scenes().size());
            assertEquals(1, log.warnings().size());
            assertTrue(log.warnings().get(0).startsWith("Could not refresh the scene catalog, keeping the last one "
                    + "(2 scenes)"), log.warnings().get(0));
        }
    }

    @DisplayName("A bridge error document with no data array keeps the last good catalog")
    @Test
    public void documentWithoutDataKeepsLastGood() throws Exception {
        refreshWith("resources-normal.json");

        when(hueBridgeClient.getResources()).thenReturn("{\"errors\":[{\"description\":\"unauthorized\"}]}");
        catalog.refresh();

        assertEquals(2, catalog.view().scenes().size());
    }

    @DisplayName("An unreachable bridge (null body) keeps the last good catalog and logs a warning")
    @Test
    public void unreachableBridgeKeepsLastGood() throws Exception {
        refreshWith("resources-normal.json");

        try (LogCapture log = new LogCapture(HueSceneCatalog.class)) {
            when(hueBridgeClient.getResources()).thenReturn(null);
            catalog.refresh();

            assertEquals(2, catalog.view().scenes().size());
            assertEquals(List.of("Hue bridge unreachable, keeping the last scene catalog (2 scenes)"),
                    log.warnings());
        }
    }

    @DisplayName("An exception from the bridge client never escapes the refresh and keeps the last catalog")
    @Test
    public void clientExceptionKeepsLastGood() throws Exception {
        refreshWith("resources-normal.json");

        when(hueBridgeClient.getResources()).thenThrow(new IllegalStateException("boom"));
        catalog.refresh();

        assertEquals(2, catalog.view().scenes().size());
    }

    @DisplayName("A refresh before any success leaves the catalog empty")
    @Test
    public void failedFirstRefreshStaysEmpty() {
        when(hueBridgeClient.getResources()).thenReturn(null);

        catalog.refresh();

        assertEquals(List.of(), catalog.view().scenes());
    }

    @DisplayName("Nothing is fetched while Hue integration is off")
    @Test
    public void skippedWhileHueDisabled() {
        configService.setHueIntegrationEnabled(false);

        catalog.refresh();

        verify(hueBridgeClient, never()).getResources();
    }

    @DisplayName("A later refresh replaces the catalog")
    @Test
    public void laterRefreshReplaces() throws Exception {
        refreshWith("resources-normal.json");
        refreshWith("resources-zone.json");

        assertEquals("scene-zone", catalog.view().scenes().get(0).id());
        assertEquals(3, catalog.view().scenes().size());
    }

    @DisplayName("Starting schedules the refresh at the configured interval and one refresh now")
    @Test
    @SuppressWarnings("unchecked")
    public void initSchedulesIntervalAndImmediateRefresh() {
        catalog.init();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(INSTANT.plusMillis(300_000L)),
                eq(Duration.ofMillis(300_000L)));
        ArgumentCaptor<Runnable> now = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(now.capture(), eq(INSTANT));
        when(hueBridgeClient.getResources()).thenReturn("{\"data\":[]}");
        now.getValue().run();
        verify(hueBridgeClient).getResources();
    }

    @DisplayName("Destroying cancels the scheduled refresh")
    @Test
    public void destroyCancels() {
        catalog.init();

        catalog.destroy();

        verify(intervalFuture).cancel(false);
    }

    @DisplayName("Changing the start scene schedules a refresh now")
    @Test
    public void startSceneChangeRefreshes() {
        catalog.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("hue.start-scene", "x")));

        verify(scheduler).schedule(any(Runnable.class), eq(INSTANT));
    }

    @DisplayName("Changing the stop scene schedules a refresh now")
    @Test
    public void stopSceneChangeRefreshes() {
        catalog.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("hue.stop-scene", "x")));

        verify(scheduler).schedule(any(Runnable.class), eq(INSTANT));
    }

    @DisplayName("An unrelated property change neither refreshes nor reschedules")
    @Test
    public void unrelatedChangeDoesNothing() {
        catalog.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("sse.heartbeat-interval", "20000")));

        verifyNoInteractions(scheduler);
    }

    @DisplayName("A new hue.scene-catalog-interval reschedules at the new interval and does not refresh")
    @Test
    public void intervalChangeReschedules() {
        catalog.init();

        catalog.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("hue.scene-catalog-interval", "60000")));

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(INSTANT.plusMillis(60_000L)),
                eq(Duration.ofMillis(60_000L)));
        verify(scheduler, org.mockito.Mockito.times(1)).schedule(any(Runnable.class), eq(INSTANT));
    }
}
