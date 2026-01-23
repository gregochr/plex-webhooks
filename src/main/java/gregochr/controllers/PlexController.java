package gregochr.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.PlexCommand;
import gregochr.beans.LocationRootBean;
import gregochr.beans.PlexRootBean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Configuration
@Validated
@ConfigurationProperties(prefix = "plexwebhooks")
public class PlexController {
    @Pattern(regexp = "^([a-zA-Z]{1,2}\\d{1,2}[a-zA-Z]{0,2})\\s*?(\\d[a-zA-Z]{2})$")
    @NotBlank
    private String postCode;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }
    @RequestMapping(value = "/plex", method = RequestMethod.POST, consumes = { "multipart/form-data" } )
    public void plexEvent(@RequestParam String payload) {
        try {
            PlexRootBean bean = new ObjectMapper().readerFor(PlexRootBean.class).readValue(payload);
            PlexCommand command = new PlexCommand(bean);

            LocationController locationController = new LocationController();
            LocationRootBean locationRootBean = locationController.getLatitudeAndLogitude("DH14HU");

            LogManager.getLogger(PlexController.class).info("[ Postcode from config : " + this.postCode + " ]");
            LogManager.getLogger(PlexController.class).info("[ Plex payload: " + payload + " ]");
            LogManager.getLogger(PlexController.class).info("[ Plex command: " + command.toString() + " ]");
            LogManager.getLogger(PlexController.class).info("[ Location root bean: " + locationRootBean.toString() + " ]");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}