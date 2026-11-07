package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SunriseSunsetTimeFactoryTest {

    private final SunriseSunsetTimeFactory factory = new SunriseSunsetTimeFactory();

    @DisplayName("getSunsetTime parses a PM time")
    @Test
    public void testGetSunsetTimePM() {
        assertEquals(Optional.of(LocalTime.of(16, 37, 54)),
                factory.getSunsetTime(new SunriseSunsetResultDto(null, "4:37:54 PM")));
    }

    @DisplayName("getSunriseTime parses an AM time")
    @Test
    public void testGetSunriseTimeAM() {
        assertEquals(Optional.of(LocalTime.of(7, 15, 30)),
                factory.getSunriseTime(new SunriseSunsetResultDto("7:15:30 AM", null)));
    }

    @DisplayName("getSunsetTime parses 12 PM as noon")
    @Test
    public void testGetSunsetTimeNoon() {
        assertEquals(Optional.of(LocalTime.NOON),
                factory.getSunsetTime(new SunriseSunsetResultDto(null, "12:00:00 PM")));
    }

    @DisplayName("getSunriseTime parses 12 AM as midnight")
    @Test
    public void testGetSunriseTimeMidnight() {
        assertEquals(Optional.of(LocalTime.MIDNIGHT),
                factory.getSunriseTime(new SunriseSunsetResultDto("12:00:00 AM", null)));
    }

    @DisplayName("getSunsetTime parses a double-digit PM hour")
    @Test
    public void testGetSunsetTimeDoubleDigitPM() {
        assertEquals(Optional.of(LocalTime.of(22, 37, 54)),
                factory.getSunsetTime(new SunriseSunsetResultDto(null, "10:37:54 PM")));
    }

    @DisplayName("getSunsetTime parses 11:59:59 PM, the last second of the day")
    @Test
    public void testGetSunsetTimeLastSecond() {
        assertEquals(Optional.of(LocalTime.of(23, 59, 59)),
                factory.getSunsetTime(new SunriseSunsetResultDto(null, "11:59:59 PM")));
    }

    @DisplayName("getSunsetTime is empty when the provider sent no result")
    @Test
    public void testGetSunsetTimeNullResult() {
        assertEquals(Optional.empty(), factory.getSunsetTime(null));
    }

    @DisplayName("getSunriseTime is empty when the provider sent no result")
    @Test
    public void testGetSunriseTimeNullResult() {
        assertEquals(Optional.empty(), factory.getSunriseTime(null));
    }

    @DisplayName("getSunsetTime is empty when the result has no sunset")
    @Test
    public void testGetSunsetTimeNullSunset() {
        assertEquals(Optional.empty(), factory.getSunsetTime(new SunriseSunsetResultDto("7:15:30 AM", null)));
    }

    @DisplayName("getSunriseTime is empty when the result has no sunrise")
    @Test
    public void testGetSunriseTimeNullSunrise() {
        assertEquals(Optional.empty(), factory.getSunriseTime(new SunriseSunsetResultDto(null, "4:37:54 PM")));
    }

    @DisplayName("getSunsetTime rejects a time that is not in the provider's format")
    @Test
    public void testGetSunsetTimeMalformed() {
        SunriseSunsetResultDto result = new SunriseSunsetResultDto(null, "16:37:54");

        assertThrows(DateTimeParseException.class, () -> factory.getSunsetTime(result));
    }
}
