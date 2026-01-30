package gregochr.controllers;

import gregochr.controllers.beans.LocationRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for default post-code (DH1 3QL)")
    @Test
    public void getLatitudeAndLongitudeTestForDH13QL() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("DH1 3QL");
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.766509);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.594375);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code with lots of spaces")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithLotsOfSpaces() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("D H 1 4 H U");
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @DisplayName("Get post code from config file is DH1 4HU")
    @Test
    public void getPostCodeFromConfig() {
        assert(locationController.getPostCode().equals("DH1 4HU"));
    }

    @DisplayName("Set and get post code")
    @Test
    public void setAndGetPostCode() {
        LocationController controller = new LocationController();
        controller.setPostCode("SW1A 1AA");
        assert(controller.getPostCode().equals("SW1A 1AA"));
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for lowercase post code")
    @Test
    public void getLatitudeAndLongitudeTestForLowercasePostCode() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("dh1 4hu");
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for post code without space")
    @Test
    public void getLatitudeAndLongitudeTestForPostCodeWithoutSpace() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("DH14HU");
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.777797);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.600031);
    }

    @Tag("integration")
    @DisplayName("Get latitude and longitude for London post code")
    @Test
    public void getLatitudeAndLongitudeTestForLondonPostCode() {
        LocationController locationController = new LocationController();
        locationController.setPostCode("SW1A 1AA");
        LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude();

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 51.50101);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -0.141563);
    }
}