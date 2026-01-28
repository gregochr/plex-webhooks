package gregochr;

import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SunriseSunsetControllerTest {

    @DisplayName("Test get sunset time for today")
    @Test
    public void testSunsetTimeForToday() {
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();
        SunriseSunsetRootBean sunriseSunsetRootBean = null;

        try {
            sunriseSunsetRootBean = sunriseSunsetController.getSunsetTimeForToday(54.777797,-1.600031);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assert(sunriseSunsetRootBean.getStatus().compareTo("OK") == 0);
        assert(sunriseSunsetRootBean.getTzId().compareTo("UTC") == 0);
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset() != null);  // The time changes every day, so lets just check its not null
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().isBlank() == false);  // The time changes every day, so lets just check its not null, etc
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().isEmpty() == false);
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().length() > 0);
    }
}
