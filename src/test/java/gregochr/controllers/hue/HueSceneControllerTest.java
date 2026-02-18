package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

@DisplayName("Hue Scene Controller Tests")
public class HueSceneControllerTest {

    private HueSceneController controller;

    @BeforeEach
    public void setUp() {
        ConfigService configService = mock(ConfigService.class);
        controller = new HueSceneController(
                "192.168.1.100",
                "001788fffe7107c8",
                "TestApplicationKey1234",
                "test-start-scene-id",
                "test-stop-scene-id",
                configService);
    }

    @DisplayName("startMovieLightScene returns false when bridge is unreachable")
    @Test
    public void testStartMovieLightSceneReturnsFalseWhenUnreachable() {
        assertFalse(controller.startMovieLightScene());
    }

    @DisplayName("stopMovieLightScene returns false when bridge is unreachable")
    @Test
    public void testStopMovieLightSceneReturnsFalseWhenUnreachable() {
        assertFalse(controller.stopMovieLightScene());
    }

    @DisplayName("onPropertiesUpdated updates start scene")
    @Test
    public void testOnPropertiesUpdatedStartScene() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "hue.start-scene", "new-start-scene-id"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("new-start-scene-id", controller.getStartScene());
    }

    @DisplayName("onPropertiesUpdated updates stop scene")
    @Test
    public void testOnPropertiesUpdatedStopScene() {
        PropertiesUpdatedEvent event = new PropertiesUpdatedEvent(this, Map.of(
                "hue.stop-scene", "new-stop-scene-id"
        ));

        controller.onPropertiesUpdated(event);

        assertEquals("new-stop-scene-id", controller.getStopScene());
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
