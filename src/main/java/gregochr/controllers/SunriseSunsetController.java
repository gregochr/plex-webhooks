package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetRootBean;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.client.RestClient;

import java.io.IOException;

public class SunriseSunsetController {
    public SunriseSunsetRootBean getSunsetTimeForToday(double latitude, double longitude) throws IOException {
        SunriseSunsetRootBean sunriseSunsetRootBean = getSunriseSunsetRootBean(latitude, longitude, null);
        return sunriseSunsetRootBean;
    }

    public SunriseSunsetRootBean getSunsetTimeForDate(double latitude, double longitude, String date) throws IOException {
        SunriseSunsetRootBean sunriseSunsetRootBean = getSunriseSunsetRootBean(latitude, longitude, date);
        return sunriseSunsetRootBean;
    }

    @Nullable
    private static SunriseSunsetRootBean getSunriseSunsetRootBean(double latitude, double longitude, String date) {
        RestClient restClient = RestClient.create();

        String sunriseSunsetServiceURL = "https://api.sunrise-sunset.org/json?lat=";;
        SunriseSunsetRootBean sunriseSunsetRootBean = null;
        if (null == date) {
            sunriseSunsetRootBean = restClient
                    .get()
                    .uri(sunriseSunsetServiceURL + "{lat}" + "&lng=" + "{lng}" + "&date=today", latitude, longitude)
                    .retrieve()
                    .body(SunriseSunsetRootBean.class);
        } else {
            sunriseSunsetRootBean = restClient
                    .get()
                    .uri(sunriseSunsetServiceURL + "{lat}" + "&lng=" + "{lng}" + "&date=" + "{date}", latitude, longitude, date)// YYYY-MM-DD
                    .retrieve()
                    .body(SunriseSunsetRootBean.class);

        }

        restClient.delete();
        LogManager.getLogger(SunriseSunsetController.class).info("[ Sunrise / sunset: " + sunriseSunsetRootBean + " ]");
        return sunriseSunsetRootBean;
    }
}
