package gregochr.controllers;

import gregochr.controllers.beans.LocationRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
public class LocationControllerTest {
    @Autowired
    LocationController locationController;

    @Tag("integration")
    @DisplayName("Get latitude and longitude for DH1 4HU")
    @Test
    public void getLatitudeAndLongitudeTestForDH14HU() {
        LocationController locationController = new LocationController();
        LocationRootBean locationRootBean = null;

        try {
            locationController.setPostCode("DH1 4HU");
            locationRootBean = locationController.getLatitudeAndLogitude();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for default post-code (DH1 3QL)")
    @Test
    public void getLatitudeAndLongitudeTestForDH13QL() {
        LocationController locationController = new LocationController();
        LocationRootBean locationRootBean = null;

        try {
            locationController.setPostCode("DH1 3QL");
            locationRootBean = locationController.getLatitudeAndLogitude();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.766509);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.594375);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code with lots of spaces")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithLotsOfSpaces() {
        LocationController locationController = new LocationController();
        LocationRootBean locationRootBean = null;

        try {
            locationController.setPostCode("D H 1 4 H U");
            locationRootBean = locationController.getLatitudeAndLogitude();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @DisplayName("Get post code from config file is DH1 4HU")
    @Test
    public void getPostCodeFromConfig() {
        assert(locationController.getPostCode().equals("DH1 4HU"));
    }
}
