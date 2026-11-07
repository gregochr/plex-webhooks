package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls the live sunrise-sunset.org API. Excluded from the build gate; run with
 * {@code ./mvnw test -Dsurefire.excludedGroups= -Dgroups=real-api}.
 */
@Tag("real-api")
public class SunriseSunsetClientRealApiTest {
    private static final double DURHAM_LAT = 54.777797;
    private static final double DURHAM_LNG = -1.600031;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final SunriseSunsetTimeFactory timeFactory = new SunriseSunsetTimeFactory();

    private static void assertWithinWindow(LocalTime expected, LocalTime actual) {
        long diff = Math.abs(Duration.between(expected, actual).toMinutes());
        assertTrue(diff <= WINDOW.toMinutes(), "expected " + actual + " within " + WINDOW + " of " + expected);
    }

    @DisplayName("Live: sunset time for today is present")
    @Test
    public void sunsetForToday() {
        SunriseSunsetResponseDto result = new SunriseSunsetClient(RestClient.create(), Clock.systemDefaultZone())
                .getSunsetTimeForToday(DURHAM_LAT, DURHAM_LNG);

        assertEquals("OK", result.status());
        assertEquals("UTC", result.tzid());
        assertNotNull(result.sunriseSunsetResultDto().sunset());
        assertFalse(result.sunriseSunsetResultDto().sunset().isBlank());
    }

    @DisplayName("Live: sunset on 28 January 2026 is near 16:37 UTC")
    @Test
    public void sunsetOn20260128() {
        SunriseSunsetResponseDto result = new SunriseSunsetClient(RestClient.create(), Clock.systemDefaultZone())
                .getSunsetTimeForDate(DURHAM_LAT, DURHAM_LNG, "2026-01-28");

        assertEquals("OK", result.status());
        assertWithinWindow(LocalTime.of(16, 37, 54),
                timeFactory.getSunsetTime(result.sunriseSunsetResultDto()).orElseThrow());
    }

    @DisplayName("Live: sunrise on 28 January 2026 is near 08:00 UTC")
    @Test
    public void sunriseOn20260128() {
        SunriseSunsetResponseDto result = new SunriseSunsetClient(RestClient.create(), Clock.systemDefaultZone())
                .getSunsetTimeForDate(DURHAM_LAT, DURHAM_LNG, "2026-01-28");

        assertEquals("OK", result.status());
        assertWithinWindow(LocalTime.of(8, 0, 44),
                timeFactory.getSunriseTime(result.sunriseSunsetResultDto()).orElseThrow());
    }

    @DisplayName("Live: summer solstice sunset is later than winter sunset")
    @Test
    public void summerSunsetLaterThanWinter() {
        SunriseSunsetClient client = new SunriseSunsetClient(RestClient.create(), Clock.systemDefaultZone());

        LocalTime summer = timeFactory.getSunsetTime(
                client.getSunsetTimeForDate(DURHAM_LAT, DURHAM_LNG, "2026-06-21").sunriseSunsetResultDto())
                .orElseThrow();
        LocalTime winter = timeFactory.getSunsetTime(
                client.getSunsetTimeForDate(DURHAM_LAT, DURHAM_LNG, "2026-01-28").sunriseSunsetResultDto())
                .orElseThrow();

        assertTrue(summer.isAfter(winter));
    }
}
