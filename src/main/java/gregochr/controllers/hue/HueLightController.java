package gregochr.controllers.hue;

import gregochr.controllers.dto.hue.HueLightDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueLightController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueLightController.class);
    private static final RestClient REST_CLIENT = RestClient.create();

    private void setLightState(final HueLightDto light, final String body) {
        final String restURL = "https://" + this.hubId + "/clip/v2/resource/light/";
        LOGGER.info("[ Turning light: {}, {} ]", light.getKey(), body);

        ResponseEntity<Void> response = REST_CLIENT
                .put()
                .uri(restURL + "{lightKey}", light.getKey())
                .header("hue-application-key", this.login)
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        LOGGER.info("[ Hue light response: {} ]", response);
    }

    private void turnLightOff(final HueLightDto light) {
        final String body = "{\"on\":{\"on\":false}}";
        setLightState(light, body);
    }

    private void turnLightOn(final HueLightDto light) {
        final String body = "{\"on\":{\"on\":true}}";
        setLightState(light, body);
    }
}
