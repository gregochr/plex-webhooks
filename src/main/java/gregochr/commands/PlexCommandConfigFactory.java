package gregochr.commands;

import gregochr.services.config.BooleanProperties;
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
                    flag(env, prefix + "-play-enabled"),
                    flag(env, prefix + "-resume-enabled"),
                    flag(env, prefix + "-stop-enabled"),
                    flag(env, prefix + "-pause-enabled"),
                    flag(env, prefix + "-scrobble-enabled")
            ));
        }
        return new PlexCommandConfig(configs);
    }

    private static boolean flag(Environment env, String key) {
        return BooleanProperties.parse(env.getProperty(key));
    }
}
