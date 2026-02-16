package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
