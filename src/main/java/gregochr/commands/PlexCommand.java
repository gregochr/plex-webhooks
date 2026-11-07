package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlexCommand {
    private static final Logger LOGGER = LogManager.getLogger(PlexCommand.class);

    private final PlexEvent plexEvent;
    private final String event;
    private final String title;
    private final int duration;
    private final PlexMediaType mediaType;

    public PlexCommand(PlexEvent plexEvent, String title, int duration, PlexMediaType mediaType) {
        this.plexEvent = plexEvent;
        this.event = plexEvent.getEvent();
        this.title = title;
        this.duration = duration;
        this.mediaType = mediaType;
    }

    public PlexCommand(String unknownEvent, PlexMediaType mediaType) {
        this.plexEvent = null;
        this.event = unknownEvent;
        this.title = "";
        this.duration = 0;
        this.mediaType = mediaType;
    }

    public String getEvent() {
        return event;
    }

    public PlexEvent getPlexEvent() {
        return plexEvent;
    }

    public String getTitle() {
        return title;
    }

    public int getDuration() {
        return duration;
    }

    public PlexMediaType getMediaType() {
        return mediaType;
    }

    @Override
    public String toString() {
        return "[ Event: %s, Title: %s, Duration: %d, Type: %s ]".formatted(
                this.event, this.title, this.duration,
                this.mediaType != null ? this.mediaType.getType() : "unknown");
    }

    /** Legacy form of {@link #run}: true only when a scene was activated. */
    public boolean execute(HueSceneController hueSceneController, ConfigService configService,
            PlexCommandConfig plexCommandConfig) {
        return run(hueSceneController, configService, plexCommandConfig).isApplied();
    }

    /**
     * Runs the command and reports what happened. The checks run in order: an unsupported event first (it never
     * touches the lights whatever the Hue switch says), then Hue disabled, an unknown media type, the per-type rule,
     * and finally the scene change itself.
     */
    public PlexOutcome run(HueSceneController hueSceneController, ConfigService configService,
            PlexCommandConfig plexCommandConfig) {
        if (plexEvent == null) {
            LOGGER.debug("[ Ignoring unrecognized Plex event: {} ]", this);
            return PlexOutcome.of(PlexOutcome.Kind.IGNORED_UNSUPPORTED);
        }

        if (!configService.isHueIntegrationEnabled()) {
            LOGGER.info("[ Hue integration disabled, skipping ]");
            return PlexOutcome.of(PlexOutcome.Kind.IGNORED_HUE_DISABLED);
        }

        if (mediaType == null) {
            LOGGER.info("[ Plex {} command has an unknown media type, skipping ]", plexEvent.name());
            return PlexOutcome.of(PlexOutcome.Kind.IGNORED_UNKNOWN_TYPE);
        }

        if (!plexCommandConfig.isCommandEnabled(plexEvent, mediaType)) {
            LOGGER.info("[ Plex {} command disabled for type {}, skipping ]", plexEvent.name(), mediaType);
            return PlexOutcome.of(PlexOutcome.Kind.IGNORED_RULE_OFF);
        }

        LOGGER.info("[ Executing Plex {} Command: {} ]", plexEvent.name(), this);
        return plexEvent.execute(hueSceneController)
                .map(PlexOutcome::applied)
                .orElseGet(() -> PlexOutcome.of(PlexOutcome.Kind.FAILED));
    }
}
