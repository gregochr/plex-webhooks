package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResponseDto;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalTime;

@Component
public class SunriseSunsetClient {
    private static final Logger LOGGER = LogManager.getLogger(SunriseSunsetClient.class);
    private static final String SUNRISE_SUNSET_SERVICE_URL = "https://api.sunrise-sunset.org/json?lat={lat}&lng={lng}&date={date}";

    private final RestClient restClient;
    private final Clock clock;

    public SunriseSunsetClient(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public SunriseSunsetResponseDto getSunsetTimeForToday(double latitude, double longitude) {
        return getSunriseSunsetResponseDto(latitude, longitude, "today");
    }

    public SunriseSunsetResponseDto getSunsetTimeForDate(double latitude, double longitude, String date) {
        return getSunriseSunsetResponseDto(latitude, longitude, date);
    }

    public boolean isAfterSunset(LocalTime sunsetTime) {
        return LocalTime.now(clock).isAfter(sunsetTime);
    }

    @Nullable
    private SunriseSunsetResponseDto getSunriseSunsetResponseDto(double latitude, double longitude, String date) {
        SunriseSunsetResponseDto sunriseSunsetResponse = restClient
                .get()
                .uri(SUNRISE_SUNSET_SERVICE_URL, latitude, longitude, date)
                .retrieve()
                .body(SunriseSunsetResponseDto.class);

        LOGGER.info("[ Sunrise / sunset: {} ]", sunriseSunsetResponse);
        if (sunriseSunsetResponse != null && sunriseSunsetResponse.sunriseSunsetResultDto() == null) {
            LOGGER.warn("[ Sunrise / sunset lookup returned status {} with no results ]",
                    sunriseSunsetResponse.status());
        }
        return sunriseSunsetResponse;
    }
}
