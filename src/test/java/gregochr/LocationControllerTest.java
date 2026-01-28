package gregochr;

import gregochr.controllers.LocationController;
import gregochr.controllers.beans.LocationRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class LocationControllerTest {
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

    @DisplayName("Get latitude and longitude for default post-code (DH1 3QL)")
    @Test
    public void getLatitudeAndLongitudeTestForDefaultPostCode() {
        LocationController locationController = new LocationController();
        LocationRootBean locationRootBean = null;

        try {
            locationRootBean = locationController.getLatitudeAndLogitude();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assert(locationRootBean.getStatus() == 200);
        assert(locationRootBean.getLocationResultBean().getLatitude() == 54.766509);
        assert(locationRootBean.getLocationResultBean().getLongitude() == -1.594375);
    }

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
}
