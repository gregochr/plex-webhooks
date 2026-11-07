package gregochr;

import gregochr.clients.LocationClient;
import gregochr.clients.dto.LocationResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("RestClient Config Tests")
public class RestClientConfigTest {

    private static LocationResponseDto lookup(String json) {
        RestClient.Builder builder = RestClientConfig.restClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        return new LocationClient(builder.build(), "DH1 4HU").getLatitudeAndLogitude();
    }

    @DisplayName("A null latitude or longitude (postcodes.io does this for some postcodes) reads as 0.0")
    @Test
    public void nullCoordinatesReadAsZero() {
        LocationResponseDto response = lookup(
                "{\"status\":200,\"result\":{\"postcode\":\"X\",\"longitude\":null,\"latitude\":null}}");

        assertEquals(0.0, response.locationResultDto().latitude());
        assertEquals(0.0, response.locationResultDto().longitude());
    }

    @DisplayName("Missing coordinates read as 0.0")
    @Test
    public void missingCoordinatesReadAsZero() {
        LocationResponseDto response = lookup("{\"status\":200,\"result\":{\"postcode\":\"X\"}}");

        assertEquals(0.0, response.locationResultDto().latitude());
        assertEquals(0.0, response.locationResultDto().longitude());
    }

    @DisplayName("A missing status reads as 0")
    @Test
    public void missingStatusReadsAsZero() {
        assertEquals(0, lookup("{\"result\":{\"latitude\":1.5,\"longitude\":2.5}}").status());
    }
}
