package gregochr.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.PlexCommand;
import gregochr.beans.HueLightBean;
import gregochr.beans.LocationRootBean;
import gregochr.beans.PlexRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class PlexController {

    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        try {
            PlexRootBean bean = new ObjectMapper().readerFor(PlexRootBean.class).readValue(payload);
            PlexCommand command = new PlexCommand(bean);

            if (command.event == PlexCommand.PlexEvent.PLAY || command.event == PlexCommand.PlexEvent.RESUME) {
                HueLightController hueLightController = new HueLightController();
                hueLightController.startMovie();
            }
            if (command.event == PlexCommand.PlexEvent.STOP || command.event == PlexCommand.PlexEvent.PAUSE ||  command.event == PlexCommand.PlexEvent.SCROBBLE) {
                HueLightController hueLightController = new HueLightController();
                hueLightController.stopMovie();
            }

            LogManager.getLogger(PlexController.class).info("[ Plex payload: " + payload + " ]");
            LogManager.getLogger(PlexController.class).info("[ Plex command: " + command.toString() + " ]");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}