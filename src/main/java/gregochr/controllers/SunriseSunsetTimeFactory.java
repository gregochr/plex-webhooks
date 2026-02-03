package gregochr.controllers;

import gregochr.controllers.beans.SunriseSunsetResultBean;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SunriseSunsetTimeFactory {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);

    public LocalTime getSunriseTime(final SunriseSunsetResultBean sunriseSunsetResultBean) {
        return parseTime(sunriseSunsetResultBean.getSunrise());
    }

    public LocalTime getSunsetTime(final SunriseSunsetResultBean sunriseSunsetResultBean) {
        return parseTime(sunriseSunsetResultBean.getSunset());
    }

    private LocalTime parseTime(final String timeString) {
        return LocalTime.parse(timeString, TIME_FORMATTER);
    }
}
