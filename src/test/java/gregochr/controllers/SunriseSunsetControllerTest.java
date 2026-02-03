package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class SunriseSunsetControllerTest {

    @Tag("integration")
    @DisplayName("Test get sunset time for today")
    @Test
    public void testSunsetTimeForToday() {
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();
        SunriseSunsetRootBean sunriseSunsetRootBean = null;

        sunriseSunsetRootBean = sunriseSunsetController.getSunsetTimeForToday(54.777797,-1.600031);

        assert(sunriseSunsetRootBean.getStatus().compareTo("OK") == 0);
        assert(sunriseSunsetRootBean.getTzId().compareTo("UTC") == 0);
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset() != null);  // The time changes every day, so lets just check its not null
        assert(!sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().isBlank());  // The time changes every day, so lets just check its not null, etc
        assert(!sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().isEmpty());
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().length() > 0);
    }
    @Tag("integration")
    @DisplayName("Test get sunset time for 28th January 2026")
    @Test
    public void testSunsetTimeFor20260128() {
        SunriseSunsetController sunriseSunsetController = new SunriseSunsetController();
        SunriseSunsetRootBean sunriseSunsetRootBean = null;

        String theDate = "2026-01-28";
        sunriseSunsetRootBean = sunriseSunsetController.getSunsetTimeForDate(54.777797,-1.600031, theDate);

        assert(sunriseSunsetRootBean.getStatus().compareTo("OK") == 0);
        assert(sunriseSunsetRootBean.getTzId().compareTo("UTC") == 0);
        assert(sunriseSunsetRootBean.getSunriseSunsetResultBean().getSunset().compareTo("4:37:54 PM") == 0);
    }

}
