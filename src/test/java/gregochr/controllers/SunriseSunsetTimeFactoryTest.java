package gregochr.controllers;

import gregochr.controllers.dto.SunriseSunsetResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SunriseSunsetTimeFactoryTest {

    @DisplayName("Test getSunsetTime parses PM time correctly")
    @Test
    public void testGetSunsetTimePM() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto(null, "4:37:54 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(result);

        assertEquals(16, sunsetTime.getHour());
        assertEquals(37, sunsetTime.getMinute());
        assertEquals(54, sunsetTime.getSecond());
    }

    @DisplayName("Test getSunriseTime parses AM time correctly")
    @Test
    public void testGetSunriseTimeAM() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto("7:15:30 AM", null);

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunriseTime = factory.getSunriseTime(result);

        assertEquals(7, sunriseTime.getHour());
        assertEquals(15, sunriseTime.getMinute());
        assertEquals(30, sunriseTime.getSecond());
    }

    @DisplayName("Test getSunsetTime parses 12 PM (noon) correctly")
    @Test
    public void testGetSunsetTimeNoon() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto(null, "12:00:00 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(result);

        assertEquals(12, sunsetTime.getHour());
        assertEquals(0, sunsetTime.getMinute());
        assertEquals(0, sunsetTime.getSecond());
    }

    @DisplayName("Test getSunriseTime parses 12 AM (midnight) correctly")
    @Test
    public void testGetSunriseTimeMidnight() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto("12:00:00 AM", null);

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunriseTime = factory.getSunriseTime(result);

        assertEquals(0, sunriseTime.getHour());
        assertEquals(0, sunriseTime.getMinute());
        assertEquals(0, sunriseTime.getSecond());
    }

    @DisplayName("Test getSunsetTime parses double-digit PM time correctly")
    @Test
    public void testGetSunsetTimeDoubleDigitPM() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto(null, "10:37:54 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(result);

        assertEquals(22, sunsetTime.getHour());
        assertEquals(37, sunsetTime.getMinute());
        assertEquals(54, sunsetTime.getSecond());
    }
}
