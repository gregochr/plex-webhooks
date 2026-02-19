package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hue")
public class HueResourceController {
    private static final Logger LOGGER = LogManager.getLogger(HueResourceController.class);

    private final HueBridgeClient hueBridgeClient;

    public HueResourceController(HueBridgeClient hueBridgeClient) {
        this.hueBridgeClient = hueBridgeClient;
    }

    @GetMapping(value = "/resources", produces = "application/json")
    public ResponseEntity<String> getResources() {
        LOGGER.info("[ Fetching all Hue resources from hub: {} ]", hueBridgeClient.getHubId());

        String body = hueBridgeClient.getResources();
        if (body != null) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(503)
                .body("{\"error\": \"Hue bridge unreachable\"}");
    }
}
