package gregochr.controllers;

import gregochr.controllers.dto.SunriseSunsetResponseDto;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalTime;

@Component
public class SunriseSunsetController {
    private static final String SUNRISE_SUNSET_SERVICE_URL = "https://api.sunrise-sunset.org/json?lat={lat}&lng={lng}&date={date}";

    private final Clock clock;

    public SunriseSunsetController() {
        this(Clock.systemDefaultZone());
    }

    public SunriseSunsetController(Clock clock) {
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

    private static final Logger LOGGER = LogManager.getLogger(SunriseSunsetController.class);
    private static final RestClient REST_CLIENT = RestClient.create();

    @Nullable
    private static SunriseSunsetResponseDto getSunriseSunsetResponseDto(double latitude, double longitude, String date) {
        SunriseSunsetResponseDto sunriseSunsetResponse = REST_CLIENT
                .get()
                .uri(SUNRISE_SUNSET_SERVICE_URL, latitude, longitude, date)
                .retrieve()
                .body(SunriseSunsetResponseDto.class);

        LOGGER.info("[ Sunrise / sunset: {} ]", sunriseSunsetResponse);
        return sunriseSunsetResponse;
    }
}