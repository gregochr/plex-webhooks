package gregochr.controllers.hue;

import gregochr.controllers.dto.hue.HueLightDto;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueLightController extends HueController {

    private void setLightState(final HueLightDto light, final String body) {
        final String restURL = "https://" + this.hubId + "/clip/v2/resource/light/";
        RestClient restClient = RestClient.create();
        LogManager.getLogger(HueLightController.class).info("[ Turning light : " + light.getKey() + ", " +  body + " ]");

        ResponseEntity<Void> response = restClient
                .put()
                .uri(restURL + "{lightKey}", light.getKey())
                .header("hue-application-key", this.login)
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        restClient.delete();
        LogManager.getLogger(HueLightController.class).info("[ Hue light response : " + response.toString() + " ]");
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
