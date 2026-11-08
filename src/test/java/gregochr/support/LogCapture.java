package gregochr.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Captures what a class logs for the length of a try-with-resources block. The application logs through the Log4j
 * API, which Spring Boot routes to Logback under the class name, so a Logback appender on that name sees it.
 */
public final class LogCapture implements AutoCloseable {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture(Class<?> loggingClass) {
        logger = (Logger) LoggerFactory.getLogger(loggingClass.getName());
        appender.start();
        logger.addAppender(appender);
    }

    /** The formatted messages logged at WARN or above. */
    public List<String> warnings() {
        return messagesAtOrAbove(Level.WARN);
    }

    /** The formatted messages logged at ERROR. */
    public List<String> errors() {
        return messagesAtOrAbove(Level.ERROR);
    }

    /** The formatted messages logged at INFO or above. */
    public List<String> infos() {
        return messagesAtOrAbove(Level.INFO);
    }

    private List<String> messagesAtOrAbove(Level level) {
        return appender.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
