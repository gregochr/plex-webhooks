package gregochr.controllers;

import gregochr.controllers.beans.LocationRootBean;
import gregochr.controllers.beans.SunriseSunsetRootBean;
import gregochr.services.plex.PlexService;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.client.RestClient;

import java.io.IOException;

public class SunriseSunsetController {
    public SunriseSunsetRootBean getSunsetTime(double latitude, double longitude) throws IOException {
        RestClient restClient = RestClient.create();
        String sunriseSunsetServiceURL = "https://api.sunrise-sunset.org/json?/";
        // https://api.sunrise-sunset.org/json?lat=36.7201600&lng=-4.4203400&date=today

        SunriseSunsetRootBean sunriseSunsetRootBean = restClient
                .get()
                .uri(sunriseSunsetServiceURL + "{lat}" + "&lng=" + "{lng}" + "&date=today", latitude, longitude)
                .retrieve()
                .body(SunriseSunsetRootBean.class);

        restClient.delete();
        LogManager.getLogger(SunriseSunsetController.class).info("[ Sunrise / sunset: " + sunriseSunsetRootBean + " ]");
        return sunriseSunsetRootBean;
    }
}
