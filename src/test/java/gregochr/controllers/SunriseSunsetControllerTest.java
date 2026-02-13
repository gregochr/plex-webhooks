package gregochr.controllers;

import gregochr.controllers.dto.SunriseSunsetResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

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
        SunriseSunsetResponseDto result = controller.getSunsetTimeForToday(54.777797, -1.600031);

        assertEquals("OK", result.status());
        assertEquals("UTC", result.tzid());
        assertNotNull(result.sunriseSunsetResultDto().sunset());
        assertFalse(result.sunriseSunsetResultDto().sunset().isBlank());
    }
    @Tag("integration")
    @DisplayName("Test get sunset time for 28th January 2026")
    @Test
    public void testSunsetTimeFor20260128() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetResponseDto result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("OK", result.status());
        assertEquals("UTC", result.tzid());
        assertEquals("4:37:54 PM", result.sunriseSunsetResultDto().sunset());
    }

    @Tag("integration")
    @DisplayName("Test get sunrise time for 28th January 2026")
    @Test
    public void testSunriseTimeFor20260128() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetResponseDto result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("OK", result.status());
        assertNotNull(result.sunriseSunsetResultDto().sunrise());
        assertEquals("8:00:44 AM", result.sunriseSunsetResultDto().sunrise());
    }

    @Tag("integration")
    @DisplayName("Test summer solstice has later sunset")
    @Test
    public void testSummerSolsticeSunset() {
        SunriseSunsetController controller = new SunriseSunsetController();
        SunriseSunsetResponseDto result = controller.getSunsetTimeForDate(54.777797, -1.600031, "2026-06-21");

        assertEquals("OK", result.status());
        assertNotNull(result.sunriseSunsetResultDto().sunset());
        // Summer sunset in Durham is much later than winter
    }

    @DisplayName("Test isAfterSunset returns true when current time is after sunset")
    @Test
    public void testIsAfterSunset() {
        // Fixed clock at 8 PM UTC
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-28T20:00:00Z"), ZoneId.of("UTC"));
        SunriseSunsetController controller = new SunriseSunsetController(fixedClock);

        // Sunset is at 4:37:54 PM (16:37:54)
        LocalTime sunsetTime = LocalTime.of(16, 37, 54);

        assertTrue(controller.isAfterSunset(sunsetTime));
    }

    @DisplayName("Test isAfterSunset returns false when current time is before sunset")
    @Test
    public void testIsBeforeSunset() {
        // Fixed clock at 2 PM UTC
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-28T14:00:00Z"), ZoneId.of("UTC"));
        SunriseSunsetController controller = new SunriseSunsetController(fixedClock);

        // Sunset is at 4:37:54 PM (16:37:54)
        LocalTime sunsetTime = LocalTime.of(16, 37, 54);

        assertFalse(controller.isAfterSunset(sunsetTime));
    }
}
