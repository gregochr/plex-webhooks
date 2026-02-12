package gregochr.controllers;

import gregochr.controllers.dto.LocationResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LocationControllerTest {
    @Autowired
    LocationController locationController;

    @Tag("integration")
    @DisplayName("Get latitude and longitude for DH1 4HU")
    @Test
    public void getLatitudeAndLongitudeTestForDH14HU() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("DH1 4HU");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(54.777797, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-1.600031, locationResponse.getLocationResultDto().getLongitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for default post-code (DH1 3QL)")
    @Test
    public void getLatitudeAndLongitudeTestForDH13QL() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("DH1 3QL");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(54.766509, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-1.594375, locationResponse.getLocationResultDto().getLongitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code with lots of spaces")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithLotsOfSpaces() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("D H 1 4 H U");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(54.777797, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-1.600031, locationResponse.getLocationResultDto().getLongitude());
    }

    @DisplayName("Get post code from config file is DH1 4HU")
    @Test
    public void getPostCodeFromConfig() {
        assertEquals("DH1 4HU", locationController.getPostCode());
    }

    @DisplayName("Set and get post code")
    @Test
    public void setAndGetPostCode() {
        LocationController controller = new LocationController();
        controller.setPostCode("SW1A 1AA");
        assertEquals("SW1A 1AA", controller.getPostCode());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for lowercase post code")
    @Test
    public void getLatitudeAndLongitudeTestForLowercasePostCode() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("dh1 4hu");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(54.777797, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-1.600031, locationResponse.getLocationResultDto().getLongitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code without space")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithoutSpace() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("DH14HU");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(54.777797, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-1.600031, locationResponse.getLocationResultDto().getLongitude());
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for London post code")
    @Test
    public void getLatitudeAndLongitudeTestForLondonPostCode() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("SW1A 1AA");
        LocationResponseDto locationResponse = locationController.getLatitudeAndLogitude();

        assertEquals(200, locationResponse.getStatus());
        assertEquals(51.50101, locationResponse.getLocationResultDto().getLatitude());
        assertEquals(-0.141563, locationResponse.getLocationResultDto().getLongitude());
    }
}
