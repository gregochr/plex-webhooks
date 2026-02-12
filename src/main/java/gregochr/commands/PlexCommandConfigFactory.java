package gregochr.commands;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.EnumMap;
import java.util.Map;

@Configuration
public class PlexCommandConfigFactory {

    @Bean
    public PlexCommandConfig plexCommandConfig(Environment env) {
        Map<PlexMediaType, PlexMediaTypeConfig> configs = new EnumMap<>(PlexMediaType.class);
        for (PlexMediaType mediaType : PlexMediaType.values()) {
            String prefix = "plex." + mediaType.getType();
            configs.put(mediaType, new PlexMediaTypeConfig(
                    env.getProperty(prefix + "-play-enabled", Boolean.class, false),
                    env.getProperty(prefix + "-resume-enabled", Boolean.class, false),
                    env.getProperty(prefix + "-stop-enabled", Boolean.class, false),
                    env.getProperty(prefix + "-pause-enabled", Boolean.class, false),
                    env.getProperty(prefix + "-scrobble-enabled", Boolean.class, false)
            ));
        }
        return new PlexCommandConfig(configs);
    }
}
