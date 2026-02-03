package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetResultBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SunriseSunsetTimeFactoryTest {

    @DisplayName("Test getSunsetTime parses PM time correctly")
    @Test
    public void testGetSunsetTimePM() {
        SunriseSunsetResultBean resultBean = new SunriseSunsetResultBean();
        resultBean.setSunset("4:37:54 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(resultBean);

        assertEquals(16, sunsetTime.getHour());
        assertEquals(37, sunsetTime.getMinute());
        assertEquals(54, sunsetTime.getSecond());
    }

    @DisplayName("Test getSunriseTime parses AM time correctly")
    @Test
    public void testGetSunriseTimeAM() {
        SunriseSunsetResultBean resultBean = new SunriseSunsetResultBean();
        resultBean.setSunrise("7:15:30 AM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunriseTime = factory.getSunriseTime(resultBean);

        assertEquals(7, sunriseTime.getHour());
        assertEquals(15, sunriseTime.getMinute());
        assertEquals(30, sunriseTime.getSecond());
    }

    @DisplayName("Test getSunsetTime parses 12 PM (noon) correctly")
    @Test
    public void testGetSunsetTimeNoon() {
        SunriseSunsetResultBean resultBean = new SunriseSunsetResultBean();
        resultBean.setSunset("12:00:00 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(resultBean);

        assertEquals(12, sunsetTime.getHour());
        assertEquals(0, sunsetTime.getMinute());
        assertEquals(0, sunsetTime.getSecond());
    }

    @DisplayName("Test getSunriseTime parses 12 AM (midnight) correctly")
    @Test
    public void testGetSunriseTimeMidnight() {
        SunriseSunsetResultBean resultBean = new SunriseSunsetResultBean();
        resultBean.setSunrise("12:00:00 AM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunriseTime = factory.getSunriseTime(resultBean);

        assertEquals(0, sunriseTime.getHour());
        assertEquals(0, sunriseTime.getMinute());
        assertEquals(0, sunriseTime.getSecond());
    }

    @DisplayName("Test getSunsetTime parses double-digit PM time correctly")
    @Test
    public void testGetSunsetTimeDoubleDigitPM() {
        SunriseSunsetResultBean resultBean = new SunriseSunsetResultBean();
        resultBean.setSunset("10:37:54 PM");

        SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();
        LocalTime sunsetTime = factory.getSunsetTime(resultBean);

        assertEquals(22, sunsetTime.getHour());
        assertEquals(37, sunsetTime.getMinute());
        assertEquals(54, sunsetTime.getSecond());
    }
}
