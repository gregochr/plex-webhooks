package gregochr.controllers.hue;

import gregochr.controllers.dto.hue.HueResourceResponseDto;
import gregochr.services.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Hue Resource Controller Tests")
public class HueResourceControllerTest {

    private HueResourceController controller;

    @BeforeEach
    public void setUp() {
        ConfigService configService = mock(ConfigService.class);
        controller = new HueResourceController(
                "192.168.1.100",
                "001788fffe7107c8",
                "TestApplicationKey1234",
                configService);
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

    @DisplayName("getResourcesParsed returns null when bridge is unreachable")
    @Test
    public void testGetResourcesParsedReturnsNullWhenUnreachable() {
        HueResourceResponseDto result = controller.getResourcesParsed();

        assertNull(result);
    }
}
