package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResultDto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SunriseSunsetTimeFactory {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);

    public LocalTime getSunriseTime(final SunriseSunsetResultDto result) {
        return parseTime(result.sunrise());
    }

    public LocalTime getSunsetTime(final SunriseSunsetResultDto result) {
        return parseTime(result.sunset());
    }

    private LocalTime parseTime(final String timeString) {
        return LocalTime.parse(timeString, TIME_FORMATTER);
    }
}
