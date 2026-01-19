package gregochr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

            LogManager.getLogger(PlexController.class).debug("Payload: " + payload);
            LogManager.getLogger(PlexController.class).info(command.toString());
            LogManager.getLogger(PlexController.class).info(locationController.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}