package gregochr.services.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("StartupInfoLogger")
public class StartupInfoLoggerTest {

    @DisplayName("describe reports the version, commit and settings file the container was started with")
    @Test
    public void testDescribeInContainer() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("APP_VERSION", "v1.2.3")
                .withProperty("GIT_COMMIT_SHA", "abc1234")
                .withProperty("cinelight.config-file", "/config/application.properties");

        assertEquals("CineLight version v1.2.3, commit abc1234, settings file /config/application.properties",
                new StartupInfoLogger(env).describe());
    }

    @DisplayName("describe falls back to dev, unknown and the classpath file outside the container")
    @Test
    public void testDescribeInDevelopment() {
        assertEquals("CineLight version dev, commit unknown, settings file classpath:application.properties",
                new StartupInfoLogger(new MockEnvironment()).describe());
    }

    @DisplayName("logBuild writes the description to the log at INFO")
    @Test
    public void testLogBuildLogsDescription() {
        Logger logger = (Logger) LoggerFactory.getLogger(StartupInfoLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new StartupInfoLogger(new MockEnvironment().withProperty("APP_VERSION", "v9")).logBuild();
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(1, appender.list.size());
        assertEquals("INFO", appender.list.get(0).getLevel().toString());
        assertEquals("CineLight version v9, commit unknown, settings file classpath:application.properties",
                appender.list.get(0).getFormattedMessage());
    }
}
