package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetRootBean;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SunriseSunsetController {
    private static final String SUNRISE_SUNSET_SERVICE_URL = "https://api.sunrise-sunset.org/json?lat={lat}&lng={lng}&date={date}";

    public SunriseSunsetRootBean getSunsetTimeForToday(double latitude, double longitude) {
        return getSunriseSunsetRootBean(latitude, longitude, "today");
    }

    public SunriseSunsetRootBean getSunsetTimeForDate(double latitude, double longitude, String date) {
        return getSunriseSunsetRootBean(latitude, longitude, date);
    }

    public boolean isAfterSunset() {
        return true;
    }

    @Nullable
    private static SunriseSunsetRootBean getSunriseSunsetRootBean(double latitude, double longitude, String date) {
        RestClient restClient = RestClient.create();

        SunriseSunsetRootBean sunriseSunsetRootBean = restClient
                .get()
                .uri(SUNRISE_SUNSET_SERVICE_URL, latitude, longitude, date)
                .retrieve()
                .body(SunriseSunsetRootBean.class);

        LogManager.getLogger(SunriseSunsetController.class).info("[ Sunrise / sunset: " + sunriseSunsetRootBean + " ]");
        return sunriseSunsetRootBean;
    }
}