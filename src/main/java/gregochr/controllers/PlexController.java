package gregochr.controllers;

import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandFactory;
import gregochr.services.events.PlaybackService;
import gregochr.services.plex.dto.PlexPayloadDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class PlexController {
    private static final Logger LOGGER = LogManager.getLogger(PlexController.class);
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Jackson 2 read a missing or null duration as 0; Plex omits it for some media, so it must not 500.
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private final PlaybackService playbackService;

    public PlexController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        PlexPayloadDto bean = OBJECT_MAPPER.readerFor(PlexPayloadDto.class).readValue(payload);
        PlexCommand command = new PlexCommandFactory(bean).getPlexCommand();

        LOGGER.info("[ Plex payload: {} ]", payload);
        LOGGER.info("[ Plex command: {} ]", command);

        playbackService.process(command, bean, payload, "plex");
    }
}
