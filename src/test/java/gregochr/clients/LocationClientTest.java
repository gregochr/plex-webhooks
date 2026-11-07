package gregochr.clients;

import gregochr.clients.dto.LocationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class LocationClientTest {
    private static final String DURHAM_JSON = """
            {"status":200,"result":{"postcode":"DH1 4HU","longitude":-1.600031,"latitude":54.777797}}
            """;

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @DisplayName("Parses status, latitude and longitude from the response")
    @Test
    public void parsesResponse() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        LocationResponseDto response = new LocationClient(restClient, "DH1 4HU").getLatitudeAndLogitude();

        assertEquals(200, response.status());
        assertEquals(54.777797, response.locationResultDto().latitude());
        assertEquals(-1.600031, response.locationResultDto().longitude());
        server.verify();
    }

    @DisplayName("Requests the postcodes.io URL with GET")
    @Test
    public void usesGet() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        new LocationClient(restClient, "DH1 4HU").getLatitudeAndLogitude();

        server.verify();
    }

    @DisplayName("Strips a single space from the post code in the URL")
    @Test
    public void stripsSingleSpace() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/SW1A1AA"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        new LocationClient(restClient, "SW1A 1AA").getLatitudeAndLogitude();

        server.verify();
    }

    @DisplayName("Strips every space from the post code in the URL")
    @Test
    public void stripsAllSpaces() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        new LocationClient(restClient, "D H 1 4 H U").getLatitudeAndLogitude();

        server.verify();
    }

    @DisplayName("Leaves a post code without spaces unchanged in the URL")
    @Test
    public void noSpacesUnchanged() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        new LocationClient(restClient, "DH14HU").getLatitudeAndLogitude();

        server.verify();
    }

    @DisplayName("Passes the post code case through unchanged")
    @Test
    public void preservesCase() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/dh14hu"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));

        new LocationClient(restClient, "dh1 4hu").getLatitudeAndLogitude();

        server.verify();
    }

    @DisplayName("setPostCode changes the post code used in the next request")
    @Test
    public void setPostCodeAffectsRequest() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/SW1A1AA"))
                .andRespond(withSuccess(DURHAM_JSON, MediaType.APPLICATION_JSON));
        LocationClient client = new LocationClient(restClient, "DH1 4HU");

        client.setPostCode("SW1A 1AA");
        client.getLatitudeAndLogitude();

        assertEquals("SW1A 1AA", client.getPostCode());
        server.verify();
    }

    @DisplayName("Get post code returns the constructor value")
    @Test
    public void getPostCode() {
        assertEquals("SW1A 1AA", new LocationClient(restClient, "SW1A 1AA").getPostCode());
    }

    @DisplayName("Ignores unknown fields in the response")
    @Test
    public void ignoresUnknownFields() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess(
                        "{\"status\":200,\"extra\":true,\"result\":{\"latitude\":1.5,\"longitude\":2.5,\"x\":\"y\"}}",
                        MediaType.APPLICATION_JSON));

        LocationResponseDto response = new LocationClient(restClient, "DH1 4HU").getLatitudeAndLogitude();

        assertEquals(1.5, response.locationResultDto().latitude());
        assertEquals(2.5, response.locationResultDto().longitude());
    }

    @DisplayName("Response without a result leaves the result null")
    @Test
    public void missingResult() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess("{\"status\":404}", MediaType.APPLICATION_JSON));

        LocationResponseDto response = new LocationClient(restClient, "DH1 4HU").getLatitudeAndLogitude();

        assertEquals(404, response.status());
        assertNull(response.locationResultDto());
    }

    @DisplayName("Empty response body yields null")
    @Test
    public void emptyBody() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/DH14HU"))
                .andRespond(withSuccess());

        assertNull(new LocationClient(restClient, "DH1 4HU").getLatitudeAndLogitude());
    }

    @DisplayName("HTTP 404 from the service is raised as RestClientResponseException")
    @Test
    public void notFoundThrows() {
        server.expect(requestTo("https://api.postcodes.io/postcodes/ZZ99ZZ"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        LocationClient client = new LocationClient(restClient, "ZZ9 9ZZ");

        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                client::getLatitudeAndLogitude);

        assertEquals(404, ex.getStatusCode().value());
    }
}
