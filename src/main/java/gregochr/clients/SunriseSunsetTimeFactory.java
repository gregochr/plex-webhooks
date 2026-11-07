package gregochr.clients;

import gregochr.clients.dto.SunriseSunsetResultDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class SunriseSunsetTimeFactory {
    private static final Logger LOGGER = LogManager.getLogger(SunriseSunsetTimeFactory.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);

    /**
     * Parses the sunrise time. Returns empty, after logging a warning, when the provider sent no result (its error
     * responses carry none) or no sunrise value.
     */
    public Optional<LocalTime> getSunriseTime(final SunriseSunsetResultDto result) {
        return parseTime(result == null ? null : result.sunrise(), "sunrise");
    }

    /**
     * Parses the sunset time. Returns empty, after logging a warning, when the provider sent no result (its error
     * responses carry none) or no sunset value.
     */
    public Optional<LocalTime> getSunsetTime(final SunriseSunsetResultDto result) {
        return parseTime(result == null ? null : result.sunset(), "sunset");
    }

    private Optional<LocalTime> parseTime(final String timeString, final String name) {
        if (timeString == null) {
            LOGGER.warn("[ No {} time in the sunrise / sunset result, carrying on without it ]", name);
            return Optional.empty();
        }
        return Optional.of(LocalTime.parse(timeString, TIME_FORMATTER));
    }
}
