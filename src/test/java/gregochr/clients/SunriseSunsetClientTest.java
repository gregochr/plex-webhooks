package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class SunriseSunsetClientTest {
    private static final String WINTER_JSON = """
            {"results":{"sunrise":"8:00:44 AM","sunset":"4:37:54 PM","solar_noon":"12:19:19 PM"},
             "status":"OK","tzid":"UTC"}
            """;
    private static final String DURHAM_20260128_URL =
            "https://api.sunrise-sunset.org/json?lat=54.777797&lng=-1.600031&date=2026-01-28";

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @DisplayName("Date request uses the exact lat, lng and date query parameters")
    @Test
    public void dateRequestUrl() {
        server.expect(requestTo(DURHAM_20260128_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(WINTER_JSON, MediaType.APPLICATION_JSON));

        new SunriseSunsetClient(restClient).getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        server.verify();
    }

    @DisplayName("Today request sends date=today")
    @Test
    public void todayRequestUrl() {
        server.expect(requestTo("https://api.sunrise-sunset.org/json?lat=51.5&lng=-0.12&date=today"))
                .andRespond(withSuccess(WINTER_JSON, MediaType.APPLICATION_JSON));

        new SunriseSunsetClient(restClient).getSunsetTimeForToday(51.5, -0.12);

        server.verify();
    }

    @DisplayName("Parses status and timezone")
    @Test
    public void parsesStatusAndTimezone() {
        server.expect(requestTo(DURHAM_20260128_URL))
                .andRespond(withSuccess(WINTER_JSON, MediaType.APPLICATION_JSON));

        SunriseSunsetResponseDto result =
                new SunriseSunsetClient(restClient).getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("OK", result.status());
        assertEquals("UTC", result.tzid());
    }

    @DisplayName("Parses the sunrise and sunset strings")
    @Test
    public void parsesSunriseAndSunset() {
        server.expect(requestTo(DURHAM_20260128_URL))
                .andRespond(withSuccess(WINTER_JSON, MediaType.APPLICATION_JSON));

        SunriseSunsetResponseDto result =
                new SunriseSunsetClient(restClient).getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals("8:00:44 AM", result.sunriseSunsetResultDto().sunrise());
        assertEquals("4:37:54 PM", result.sunriseSunsetResultDto().sunset());
    }

    @DisplayName("Parsed sunset string feeds SunriseSunsetTimeFactory")
    @Test
    public void parsedSunsetFeedsTimeFactory() {
        server.expect(requestTo(DURHAM_20260128_URL))
                .andRespond(withSuccess(WINTER_JSON, MediaType.APPLICATION_JSON));

        SunriseSunsetResponseDto result =
                new SunriseSunsetClient(restClient).getSunsetTimeForDate(54.777797, -1.600031, "2026-01-28");

        assertEquals(LocalTime.of(16, 37, 54),
                new SunriseSunsetTimeFactory().getSunsetTime(result.sunriseSunsetResultDto()));
    }

    @DisplayName("Non-OK provider status is passed through")
    @Test
    public void nonOkStatus() {
        server.expect(requestTo("https://api.sunrise-sunset.org/json?lat=0.0&lng=0.0&date=bad"))
                .andRespond(withSuccess("{\"results\":{},\"status\":\"INVALID_DATE\",\"tzid\":\"UTC\"}",
                        MediaType.APPLICATION_JSON));

        SunriseSunsetResponseDto result = new SunriseSunsetClient(restClient).getSunsetTimeForDate(0, 0, "bad");

        assertEquals("INVALID_DATE", result.status());
    }

    @DisplayName("Provider error body with results as an empty string is raised as RestClientException")
    @Test
    public void emptyStringResultsThrows() {
        server.expect(requestTo("https://api.sunrise-sunset.org/json?lat=0.0&lng=0.0&date=bad"))
                .andRespond(withSuccess("{\"results\":\"\",\"status\":\"INVALID_DATE\",\"tzid\":\"UTC\"}",
                        MediaType.APPLICATION_JSON));
        SunriseSunsetClient client = new SunriseSunsetClient(restClient);

        assertThrows(RestClientException.class, () -> client.getSunsetTimeForDate(0, 0, "bad"));
    }

    @DisplayName("Empty response body yields null")
    @Test
    public void emptyBody() {
        server.expect(requestTo("https://api.sunrise-sunset.org/json?lat=1.0&lng=2.0&date=today"))
                .andRespond(withSuccess());

        assertNull(new SunriseSunsetClient(restClient).getSunsetTimeForToday(1.0, 2.0));
    }

    @DisplayName("HTTP 500 from the service is raised as RestClientResponseException")
    @Test
    public void serverErrorThrows() {
        server.expect(requestTo("https://api.sunrise-sunset.org/json?lat=1.0&lng=2.0&date=today"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        SunriseSunsetClient client = new SunriseSunsetClient(restClient);

        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.getSunsetTimeForToday(1.0, 2.0));

        assertEquals(500, ex.getStatusCode().value());
    }

    @DisplayName("isAfterSunset returns true when current time is after sunset")
    @Test
    public void isAfterSunsetTrue() {
        Clock clock = Clock.fixed(Instant.parse("2024-01-28T20:00:00Z"), ZoneId.of("UTC"));

        assertTrue(new SunriseSunsetClient(restClient, clock).isAfterSunset(LocalTime.of(16, 37, 54)));
    }

    @DisplayName("isAfterSunset returns false when current time is before sunset")
    @Test
    public void isAfterSunsetFalse() {
        Clock clock = Clock.fixed(Instant.parse("2024-01-28T14:00:00Z"), ZoneId.of("UTC"));

        assertFalse(new SunriseSunsetClient(restClient, clock).isAfterSunset(LocalTime.of(16, 37, 54)));
    }

    @DisplayName("isAfterSunset returns false at exactly sunset")
    @Test
    public void isAfterSunsetFalseAtBoundary() {
        Clock clock = Clock.fixed(Instant.parse("2024-01-28T16:37:54Z"), ZoneId.of("UTC"));

        assertFalse(new SunriseSunsetClient(restClient, clock).isAfterSunset(LocalTime.of(16, 37, 54)));
    }
}
