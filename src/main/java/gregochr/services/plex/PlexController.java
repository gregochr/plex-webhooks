package gregochr.services.plex;

import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandFactory;
import gregochr.controllers.LocationController;
import gregochr.controllers.SunriseSunsetController;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.config.ConfigService;
import gregochr.services.plex.dto.PlexPayloadDto;
import gregochr.services.sse.SseEventDto;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PlexController {
    private final HueSceneController hueSceneController;
    private final LocationController locationController;
    private final SunriseSunsetController sunriseSunsetController;
    private final SseService sseService;
    private final ConfigService configService;

    public PlexController(HueSceneController hueSceneController,
                          LocationController locationController,
                          SunriseSunsetController sunriseSunsetController,
                          SseService sseService,
                          ConfigService configService) {
        this.hueSceneController = hueSceneController;
        this.locationController = locationController;
        this.sunriseSunsetController = sunriseSunsetController;
        this.sseService = sseService;
        this.configService = configService;
    }

    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        try {
            PlexPayloadDto bean = new ObjectMapper().readerFor(PlexPayloadDto.class).readValue(payload);
            PlexCommand command = new PlexCommandFactory(bean).getPlexCommand();

            LogManager.getLogger(PlexController.class).info("[ Plex payload: " + payload + " ]");
            LogManager.getLogger(PlexController.class).info("[ Plex command: " + command + " ]");

            // Broadcast to SSE clients
            sseService.broadcast(new SseEventDto(command, payload));

            command.execute(hueSceneController, locationController, sunriseSunsetController, configService);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
