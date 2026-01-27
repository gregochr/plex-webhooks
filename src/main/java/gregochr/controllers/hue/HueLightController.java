package gregochr.controllers.hue;

import gregochr.controllers.beans.hue.HueLightBean;
import gregochr.commands.PlexPlayCommand;
import gregochr.controllers.LocationController;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HueLightController extends HueController {
    private final String hubAddress = "192.168.4.38/";
    private final String hubId = "001788fffe7107c8";
    private final String login = "UndoIYT7IxLAEiJsAnLN4nBwynr9xmSUgoYcywvh";
    private final String key = "EDE7190A863DB7F0CEC20E4E35BF95F1";

    private void setLightState(final HueLightBean light, final String body) {
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
                //.body(HueLightResponseBean.class);

        restClient.delete();
        LogManager.getLogger(HueLightController.class).info("[ Hue light response : " + response.toString() + " ]");
    }
    private void setScene(final String sceneId) {
        final String restURL = "https://" + this.hubId + "/clip/v2/resource/sceneId/";
        final String body = "{\"recall\":{\"action\":\"active\", \"transitiontime\":50}}";

        RestClient restClient = RestClient.create();
        LogManager.getLogger(HueLightController.class).info("[ Turning sceneId : " + sceneId + ", " +  body + " ]");

        ResponseEntity<Void> response = restClient
                .put()
                .uri(restURL + "{sceneKey}", sceneId)
                .header("hue-application-key", this.login)
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        //.body(HueLightResponseBean.class);

        restClient.delete();
        LogManager.getLogger(HueLightController.class).info("[ Hue sceneId response : " + response.toString() + " ]");
    }
    private void turnLightOff(final HueLightBean light) {
        final String body = "{\"on\":{\"on\":false}}";
        setLightState(light, body);
    }
    private void turnLightOn(final HueLightBean light) {
        final String body = "{\"on\":{\"on\":true}}";
        setLightState(light, body);
    }

}
