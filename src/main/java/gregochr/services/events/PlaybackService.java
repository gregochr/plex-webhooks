package gregochr.services.events;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandConfig;
import gregochr.commands.PlexOutcome;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * The one pipeline every Plex event goes through, whether it came from the Plex webhook or the Debug endpoint:
 * run the command, record what happened, then broadcast it. Executing first means the broadcast carries the outcome.
 *
 * <p>Assigning the id, appending to the history, updating the now-playing state and broadcasting happen in one
 * critical section, so two concurrent webhooks can never be recorded in one order and broadcast in another. The Hue
 * call itself runs outside the lock.
 */
@Service
public class PlaybackService {
    private static final Logger LOGGER = LogManager.getLogger(PlaybackService.class);

    private static final PlexMetadataDto NO_METADATA = new PlexMetadataDto(null, 0, null);

    private final HueSceneController hueSceneController;
    private final ConfigService configService;
    private final PlexCommandConfig plexCommandConfig;
    private final SseService sseService;
    private final PlexPayloadSanitizer sanitizer;
    private final PlaybackHistory history;
    private final Clock clock;
    private final Object lock = new Object();
    private long nextId = 1;
    private volatile NowPlaying nowPlaying = NowPlaying.idle();

    public PlaybackService(HueSceneController hueSceneController, ConfigService configService,
            PlexCommandConfig plexCommandConfig, SseService sseService, PlexPayloadSanitizer sanitizer,
            PlaybackHistory history, Clock clock) {
        this.hueSceneController = hueSceneController;
        this.configService = configService;
        this.plexCommandConfig = plexCommandConfig;
        this.sseService = sseService;
        this.sanitizer = sanitizer;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Runs, records and broadcasts one event. A {@link RuntimeException} from the Hue path is logged and recorded as
     * {@code FAILED}; it never propagates, so the Plex webhook still answers 200.
     *
     * @param command    the command to run
     * @param payload    the parsed payload, for the title, year, player and so on
     * @param rawPayload the payload as JSON text; sanitised before it is stored or broadcast
     * @param source     {@code plex} or {@code debug}
     * @return the recorded event
     */
    public PlaybackEvent process(PlexCommand command, PlexPayloadDto payload, String rawPayload, String source) {
        PlexOutcome outcome = runCommand(command);
        String cleanPayload = sanitizer.sanitise(rawPayload);
        PlexMetadataDto metadata = payload.metadata() != null ? payload.metadata() : NO_METADATA;
        String mediaType = command.getMediaType() != null ? command.getMediaType().getType() : metadata.type();
        String player = payload.player() != null ? payload.player().title() : null;

        synchronized (lock) {
            PlaybackEvent event = new PlaybackEvent(
                    nextId++,
                    clock.instant().toString(),
                    command.getEvent(),
                    mediaType,
                    metadata.title(),
                    metadata.grandparentTitle(),
                    metadata.parentIndex(),
                    metadata.index(),
                    metadata.year(),
                    player,
                    outcome.kind(),
                    outcome.sceneId(),
                    source,
                    cleanPayload,
                    new PlaybackEvent.Command(command.getEvent(), command.getTitle(), command.getDuration()));
            if (event.isMedia()) {
                history.add(event);
                nowPlaying = nowPlaying.apply(event, command.getPlexEvent());
            }
            sseService.broadcast(event);
            if (event.isMedia()) {
                sseService.broadcastNowPlaying(nowPlaying);
            }
            return event;
        }
    }

    /** The recorded media events, newest first. */
    public List<PlaybackEvent> recent(int limit) {
        return history.newestFirst(limit);
    }

    public NowPlaying nowPlaying() {
        return nowPlaying;
    }

    private PlexOutcome runCommand(PlexCommand command) {
        try {
            return command.run(hueSceneController, configService, plexCommandConfig);
        } catch (RuntimeException e) {
            LOGGER.warn("[ Plex command {} failed: {} ]", command, e.getMessage());
            return PlexOutcome.of(PlexOutcome.Kind.FAILED);
        }
    }
}
