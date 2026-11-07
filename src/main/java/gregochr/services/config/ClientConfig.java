package gregochr.services.config;

import java.util.Map;

/**
 * The configuration a dashboard needs, in one shape for both {@code GET /api/config} and the {@code config} SSE
 * event. It is built from named fields, never from the properties file, so it cannot carry a credential.
 *
 * @param hueIntegrationEnabled whether Plex events may change the lights
 * @param scenes                the scenes activated on play and on pause or stop
 * @param plexCommands          for each media type, the per-event switches keyed as in {@code application.properties}
 *                              (for example {@code plex.movie-play-enabled})
 */
public record ClientConfig(boolean hueIntegrationEnabled, Scenes scenes,
        Map<String, Map<String, Boolean>> plexCommands) {

    /** The scene ids (Hue UUIDs, not credentials). */
    public record Scenes(String start, String stop) {
    }
}
