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
            PlexEventBean bean = new ObjectMapper().readerFor(PlexEventBean.class).readValue(payload);
            PlexCommand command = new PlexCommand(bean);

            LogManager.getLogger(PlexController.class).info("Configuration File Defined To Be :: "+System.getProperty("log4j.configurationFile"));
            LogManager.getLogger(PlexController.class).debug("Payload: " + payload);
            LogManager.getLogger(PlexController.class).info(command.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}