package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Hue Resource Controller Tests")
public class HueResourceControllerTest {

    private HueResourceController controller;
    private HueBridgeClient hueBridgeClient;

    @BeforeEach
    public void setUp() {
        hueBridgeClient = mock(HueBridgeClient.class);
        when(hueBridgeClient.getHubId()).thenReturn("001788fffe7107c8");
        when(hueBridgeClient.getResources()).thenReturn(null);
        controller = new HueResourceController(hueBridgeClient);
    }

    @DisplayName("getResources returns 503 when bridge is unreachable")
    @Test
    public void testGetResourcesReturns503WhenUnreachable() {
        ResponseEntity<String> response = controller.getResources();

        assertEquals(503, response.getStatusCode().value());
    }

    @DisplayName("getResources returns error JSON body when bridge is unreachable")
    @Test
    public void testGetResourcesReturnsErrorBodyWhenUnreachable() {
        ResponseEntity<String> response = controller.getResources();

        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Hue bridge unreachable"));
    }

    @DisplayName("getResources returns non-null response")
    @Test
    public void testGetResourcesReturnsNonNullResponse() {
        ResponseEntity<String> response = controller.getResources();

        assertNotNull(response);
    }

    @DisplayName("getResources error body is valid JSON")
    @Test
    public void testGetResourcesErrorBodyIsValidJson() {
        ResponseEntity<String> response = controller.getResources();

        String body = response.getBody();
        assertNotNull(body);
        assertEquals("{\"error\": \"Hue bridge unreachable\"}", body);
    }
}
