package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Hue Resource Controller Tests")
public class HueResourceControllerTest {

    private static final String RESOURCES_JSON = "{\"errors\":[],\"data\":[{\"id\":\"scene-1\",\"type\":\"scene\"}]}";

    private HueResourceController controller;
    private HueBridgeClient hueBridgeClient;

    @BeforeEach
    public void setUp() {
        hueBridgeClient = mock(HueBridgeClient.class);
        when(hueBridgeClient.getHubId()).thenReturn("001788fffe7107c8");
        controller = new HueResourceController(hueBridgeClient);
    }

    @DisplayName("getResources returns 200 when the bridge answers")
    @Test
    public void testGetResourcesReturns200() {
        when(hueBridgeClient.getResources()).thenReturn(RESOURCES_JSON);

        assertEquals(200, controller.getResources().getStatusCode().value());
    }

    @DisplayName("getResources passes the bridge's JSON through unchanged")
    @Test
    public void testGetResourcesReturnsBridgeBody() {
        when(hueBridgeClient.getResources()).thenReturn(RESOURCES_JSON);

        assertEquals(RESOURCES_JSON, controller.getResources().getBody());
    }

    @DisplayName("getResources passes through an empty JSON object from the bridge")
    @Test
    public void testGetResourcesReturnsEmptyObject() {
        when(hueBridgeClient.getResources()).thenReturn("{}");

        ResponseEntity<String> response = controller.getResources();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{}", response.getBody());
    }

    @DisplayName("getResources returns 503 when the bridge is unreachable")
    @Test
    public void testGetResourcesReturns503WhenUnreachable() {
        when(hueBridgeClient.getResources()).thenReturn(null);

        assertEquals(503, controller.getResources().getStatusCode().value());
    }

    @DisplayName("getResources returns the error JSON body when the bridge is unreachable")
    @Test
    public void testGetResourcesReturnsErrorBodyWhenUnreachable() {
        when(hueBridgeClient.getResources()).thenReturn(null);

        assertEquals("{\"error\": \"Hue bridge unreachable\"}", controller.getResources().getBody());
    }
}
