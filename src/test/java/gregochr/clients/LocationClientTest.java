package gregochr.clients;

import gregochr.clients.dto.LocationResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LocationClientTest {
    @Autowired
    LocationClient locationClient;

    @Tag("integration")
    @DisplayName("Get latitude and longitude for DH1 4HU")
    @Test
    public void getLatitudeAndLongitudeTestForDH14HU() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "DH1 4HU");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(54.777797, locationResponse.locationResultDto().latitude());
        assertEquals(-1.600031, locationResponse.locationResultDto().longitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for default post-code (DH1 3QL)")
    @Test
    public void getLatitudeAndLongitudeTestForDH13QL() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "DH1 3QL");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(54.766509, locationResponse.locationResultDto().latitude());
        assertEquals(-1.594375, locationResponse.locationResultDto().longitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code with lots of spaces")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithLotsOfSpaces() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "D H 1 4 H U");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(54.777797, locationResponse.locationResultDto().latitude());
        assertEquals(-1.600031, locationResponse.locationResultDto().longitude());
    }

    @DisplayName("Get post code from config file is DH1 4HU")
    @Test
    public void getPostCodeFromConfig() {
        assertEquals("DH1 4HU", locationClient.getPostCode());
    }

    @DisplayName("Set and get post code")
    @Test
    public void setAndGetPostCode() {
        LocationClient client = new LocationClient(RestClient.create(), "SW1A 1AA");
        assertEquals("SW1A 1AA", client.getPostCode());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for lowercase post code")
    @Test
    public void getLatitudeAndLongitudeTestForLowercasePostCode() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "dh1 4hu");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(54.777797, locationResponse.locationResultDto().latitude());
        assertEquals(-1.600031, locationResponse.locationResultDto().longitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code without space")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithoutSpace() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "DH14HU");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(54.777797, locationResponse.locationResultDto().latitude());
        assertEquals(-1.600031, locationResponse.locationResultDto().longitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for London post code")
    @Test
    public void getLatitudeAndLongitudeTestForLondonPostCode() {
        LocationClient locationClient = new LocationClient(RestClient.create(), "SW1A 1AA");
        LocationResponseDto locationResponse = locationClient.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.status());
        assertEquals(51.50101, locationResponse.locationResultDto().latitude());
        assertEquals(-0.141563, locationResponse.locationResultDto().longitude());
    }
}
