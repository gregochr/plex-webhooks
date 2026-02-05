package gregochr.controllers;

import gregochr.controllers.dto.SunriseSunsetResponseDto;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
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

    @Nullable
    private static SunriseSunsetResponseDto getSunriseSunsetResponseDto(double latitude, double longitude, String date) {
        RestClient restClient = RestClient.create();

        SunriseSunsetResponseDto sunriseSunsetResponse = restClient
                .get()
                .uri(SUNRISE_SUNSET_SERVICE_URL, latitude, longitude, date)
                .retrieve()
                .body(SunriseSunsetResponseDto.class);

        LogManager.getLogger(SunriseSunsetController.class).info("[ Sunrise / sunset: " + sunriseSunsetResponse + " ]");
        return sunriseSunsetResponse;
    }
}