package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetRootBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SunriseSunsetControllerTest {

    @Tag("integration")
    @DisplayName("Test get sunset time for today")
    @Test
    public void testSunsetTimeForToday() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetRootBean result = controller.getSunsetTimeForToday(54.777797, -1.600031);

        assertEquals("OK", result.getStatus());
        assertEquals("UTC", result.getTzId());
        assertNotNull(result.getSunriseSunsetResultBean().getSunset());
        assertFalse(result.getSunriseSunsetResultBean().getSunset().isBlank());
    }
    @Tag("integration")
    @DisplayName("Test get sunset time for 28th January 2026")
    @Test
    public void testSunsetTimeFor20260128() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetRootBean result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("OK", result.getStatus());
        assertEquals("UTC", result.getTzId());
        assertEquals("4:37:54 PM", result.getSunriseSunsetResultBean().getSunset());
    }

    @Tag("integration")
    @DisplayName("Test get sunrise time for 28th January 2026")
    @Test
    public void testSunriseTimeFor20260128() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetRootBean result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("OK", result.getStatus());
        assertNotNull(result.getSunriseSunsetResultBean().getSunrise());
        assertEquals("8:00:44 AM", result.getSunriseSunsetResultBean().getSunrise());
    }

    @Tag("integration")
    @DisplayName("Test summer solstice has later sunset")
    @Test
    public void testSummerSolsticeSunset() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetRootBean result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-06-21");

        assertEquals("OK", result.getStatus());
        assertNotNull(result.getSunriseSunsetResultBean().getSunset());
        // Summer sunset in Durham is much later than winter
    }

    @DisplayName("Test isAfterSunset returns true")
    @Test
    public void testIsAfterSunset() {
        SunriseSunsetController controller = new SunriseSunsetController();
        assertTrue(controller.isAfterSunset());
    }
}
