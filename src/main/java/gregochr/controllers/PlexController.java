package gregochr.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.PlexCommand;
import gregochr.beans.LocationRootBean;
import gregochr.beans.PlexRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlexController {

    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        try {
            PlexRootBean bean = new ObjectMapper().readerFor(PlexRootBean.class).readValue(payload);
            PlexCommand command = new PlexCommand(bean);

            LocationController locationController = new LocationController();
            LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude("DH14HU");

            LogManager.getLogger(PlexController.class).info("Plex payload: " + payload);
            LogManager.getLogger(PlexController.class).info("Plex command: " + command.toString());
            LogManager.getLogger(PlexController.class).info("Location root bean: " + locationRootBean.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}