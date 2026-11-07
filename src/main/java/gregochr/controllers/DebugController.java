package gregochr.controllers;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexEvent;
import gregochr.commands.PlexMediaType;
import gregochr.commands.PlexOutcome;
import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackService;
import gregochr.services.plex.dto.PlexMetadataDto;
import gregochr.services.plex.dto.PlexPayloadDto;
import gregochr.services.plex.dto.PlexPlayerDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Sends a test event down the same pipeline as a real Plex webhook, so it runs the scene, is recorded in the history
 * and is broadcast to dashboards (marked {@code source: debug}).
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {
    private static final Logger LOGGER = LogManager.getLogger(DebugController.class);
    private static final String PLAYER_NAME = "CineLight debug";
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final PlaybackService playbackService;

    public DebugController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping("/{command}")
    public Map<String, Object> triggerCommand(
            @PathVariable String command,
            @RequestParam(defaultValue = "movie") String type,
            @RequestParam(required = false) String title) {

        PlexMediaType mediaType = PlexMediaType.fromType(type).orElse(null);
        String eventTitle = title != null && !title.isBlank() ? title : defaultTitle(command);
        PlexCommand plexCommand = createCommand(command, eventTitle, mediaType);
        LOGGER.info("[ Debug: Triggering {} command with type: {} ]", plexCommand, type);

        PlexPayloadDto payload = new PlexPayloadDto(plexCommand.getEvent(),
                new PlexMetadataDto(eventTitle, 0, type), new PlexPlayerDto(PLAYER_NAME));
        PlaybackEvent event = playbackService.process(plexCommand, payload, syntheticPayload(payload), "debug");

        return Map.of("command", command, "type", type, "success", event.outcome() == PlexOutcome.Kind.SCENE_APPLIED,
                "outcome", event.outcome().name(), "id", event.id());
    }

    /** A payload of the same shape Plex sends, built by Jackson so a title can never break out of the JSON. */
    private static String syntheticPayload(PlexPayloadDto payload) {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "event", payload.event(),
                "Player", Map.of("title", payload.player().title()),
                "Metadata", Map.of("type", payload.metadata().type(), "title", payload.metadata().title(),
                        "duration", payload.metadata().duration())));
    }

    private static PlexCommand createCommand(String command, String title, PlexMediaType mediaType) {
        return switch (command) {
            case "play" -> new PlexCommand(PlexEvent.PLAY, title, 0, mediaType);
            case "stop" -> new PlexCommand(PlexEvent.STOP, title, 0, mediaType);
            case "pause" -> new PlexCommand(PlexEvent.PAUSE, title, 0, mediaType);
            case "resume" -> new PlexCommand(PlexEvent.RESUME, title, 0, mediaType);
            case "scrobble" -> new PlexCommand(PlexEvent.SCROBBLE, title, 0, mediaType);
            default -> new PlexCommand(command, mediaType);
        };
    }

    private static String defaultTitle(String command) {
        return switch (command) {
            case "play" -> "Debug Play Command Test";
            case "stop" -> "Debug Stop Command Test";
            case "pause" -> "Debug Pause Test";
            case "resume" -> "Debug Resume Test";
            case "scrobble" -> "Debug Scrobble Test";
            default -> "Debug " + command + " Test";
        };
    }
}
