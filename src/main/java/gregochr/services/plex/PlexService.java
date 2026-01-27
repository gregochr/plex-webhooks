package gregochr.services.plex;

import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.commands.PlexCommand;
import gregochr.commands.PlexCommandFactory;
import gregochr.controllers.hue.HueSceneController;
import gregochr.services.plex.beans.PlexRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
public class PlexService {

    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        try {
            PlexRootBean bean = new ObjectMapper().readerFor(PlexRootBean.class).readValue(payload);
            PlexCommand command = new PlexCommandFactory(bean).getPlexCommand();
            HueSceneController hueSceneController = new HueSceneController();

            LogManager.getLogger(PlexService.class).info("[ Plex payload: " + payload + " ]");
            LogManager.getLogger(PlexService.class).info("[ Plex command: " + command + " ]");

            command.execute(hueSceneController);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}