package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @DisplayName("startMovieLightScene returns true when the bridge activates the scene")
    @Test
    public void testStartMovieLightSceneReturnsTrue() {
        when(hueBridgeClient.activateScene("test-start-scene-id")).thenReturn(true);

        assertTrue(controller.startMovieLightScene());
    }

    @DisplayName("startMovieLightScene returns false when the bridge is unreachable")
    @Test
    public void testStartMovieLightSceneReturnsFalseWhenUnreachable() {
        when(hueBridgeClient.activateScene("test-start-scene-id")).thenReturn(false);

        assertFalse(controller.startMovieLightScene());
    }

    @DisplayName("stopMovieLightScene returns true when the bridge activates the scene")
    @Test
    public void testStopMovieLightSceneReturnsTrue() {
        when(hueBridgeClient.activateScene("test-stop-scene-id")).thenReturn(true);

        assertTrue(controller.stopMovieLightScene());
    }

    @DisplayName("stopMovieLightScene returns false when the bridge is unreachable")
    @Test
    public void testStopMovieLightSceneReturnsFalseWhenUnreachable() {
        when(hueBridgeClient.activateScene("test-stop-scene-id")).thenReturn(false);

        assertFalse(controller.stopMovieLightScene());
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
