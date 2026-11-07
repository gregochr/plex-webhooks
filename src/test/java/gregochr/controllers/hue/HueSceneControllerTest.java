package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Hue Scene Controller Tests")
public class HueSceneControllerTest {

    private HueSceneController controller;
    private HueBridgeClient hueBridgeClient;

    @BeforeEach
    public void setUp() {
        hueBridgeClient = mock(HueBridgeClient.class);
        controller = new HueSceneController(
                hueBridgeClient,
                "test-start-scene-id",
                "test-stop-scene-id");
    }

    @DisplayName("startMovieLightScene activates the start scene and nothing else")
    @Test
    public void testStartMovieLightSceneActivatesStartScene() {
        controller.startMovieLightScene();

        verify(hueBridgeClient).activateScene("test-start-scene-id");
        verifyNoMoreInteractions(hueBridgeClient);
    }

    @DisplayName("stopMovieLightScene activates the stop scene and nothing else")
    @Test
    public void testStopMovieLightSceneActivatesStopScene() {
        controller.stopMovieLightScene();

        verify(hueBridgeClient).activateScene("test-stop-scene-id");
        verifyNoMoreInteractions(hueBridgeClient);
    }

    @DisplayName("startMovieLightScene returns the id of the scene the bridge activated")
    @Test
    public void testStartMovieLightSceneReturnsSceneId() {
        when(hueBridgeClient.activateScene("test-start-scene-id")).thenReturn(true);

        assertEquals(Optional.of("test-start-scene-id"), controller.startMovieLightScene());
    }

    @DisplayName("startMovieLightScene returns empty when the bridge is unreachable")
    @Test
    public void testStartMovieLightSceneReturnsEmptyWhenUnreachable() {
        when(hueBridgeClient.activateScene("test-start-scene-id")).thenReturn(false);

        assertEquals(Optional.empty(), controller.startMovieLightScene());
    }

    @DisplayName("stopMovieLightScene returns the id of the scene the bridge activated")
    @Test
    public void testStopMovieLightSceneReturnsSceneId() {
        when(hueBridgeClient.activateScene("test-stop-scene-id")).thenReturn(true);

        assertEquals(Optional.of("test-stop-scene-id"), controller.stopMovieLightScene());
    }

    @DisplayName("stopMovieLightScene returns empty when the bridge is unreachable")
    @Test
    public void testStopMovieLightSceneReturnsEmptyWhenUnreachable() {
        when(hueBridgeClient.activateScene("test-stop-scene-id")).thenReturn(false);

        assertEquals(Optional.empty(), controller.stopMovieLightScene());
    }

    @DisplayName("After a hot reload the id returned is the new scene's id")
    @Test
    public void testHotReloadedSceneIdIsReturned() {
        controller.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of("hue.start-scene", "new-start")));
        when(hueBridgeClient.activateScene("new-start")).thenReturn(true);

        assertEquals(Optional.of("new-start"), controller.startMovieLightScene());
    }

    @DisplayName("onPropertiesUpdated updates the start scene")
    @Test
    public void testOnPropertiesUpdatedStartScene() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "hue.start-scene", "new-start-scene-id"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("new-start-scene-id", controller.getStartScene());
        assertEquals("test-stop-scene-id", controller.getStopScene());
    }

    @DisplayName("onPropertiesUpdated updates the stop scene")
    @Test
    public void testOnPropertiesUpdatedStopScene() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "hue.stop-scene", "new-stop-scene-id"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("new-stop-scene-id", controller.getStopScene());
        assertEquals("test-start-scene-id", controller.getStartScene());
    }

    @DisplayName("onPropertiesUpdated updates both scenes from one event")
    @Test
    public void testOnPropertiesUpdatedBothScenes() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "hue.start-scene", "new-start-scene-id",
                "hue.stop-scene", "new-stop-scene-id"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("new-start-scene-id", controller.getStartScene());
        assertEquals("new-stop-scene-id", controller.getStopScene());
    }

    @DisplayName("After a hot reload, the new scene ids are the ones activated")
    @Test
    public void testHotReloadedScenesAreActivated() {
        controller.onPropertiesUpdated(new PropertiesUpdatedEvent(this, Map.of(
                "hue.start-scene", "new-start-scene-id",
                "hue.stop-scene", "new-stop-scene-id"
        )));

        controller.startMovieLightScene();
        controller.stopMovieLightScene();

        verify(hueBridgeClient).activateScene("new-start-scene-id");
        verify(hueBridgeClient).activateScene("new-stop-scene-id");
        verifyNoMoreInteractions(hueBridgeClient);
    }

    @DisplayName("onPropertiesUpdated ignores unrelated keys")
    @Test
    public void testOnPropertiesUpdatedIgnoresUnrelatedKeys() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "sse.heartbeat-interval", "60000"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("test-start-scene-id", controller.getStartScene());
        assertEquals("test-stop-scene-id", controller.getStopScene());
    }
}
