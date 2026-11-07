package gregochr.services.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs which build is running. The Docker image sets {@code APP_VERSION} and {@code GIT_COMMIT_SHA} from the
 * release tag; a plain {@code spring-boot:run} has neither, so they read as {@code dev} and {@code unknown}.
 */
@Component
public class StartupInfoLogger {
    private static final Logger LOGGER = LogManager.getLogger(StartupInfoLogger.class);

    private final Environment environment;

    public StartupInfoLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logBuild() {
        LOGGER.info(describe());
    }

    String describe() {
        String settingsFile = environment.getProperty(
                PropertiesRepository.CONFIG_FILE_PROPERTY, "classpath:application.properties");
        return "CineLight version " + environment.getProperty("APP_VERSION", "dev")
                + ", commit " + environment.getProperty("GIT_COMMIT_SHA", "unknown")
                + ", settings file " + settingsFile;
    }
}
