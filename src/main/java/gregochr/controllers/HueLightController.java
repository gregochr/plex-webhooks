package gregochr.controllers;

import gregochr.beans.HueLightBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HueLightController {
    private final String hubAddress = "192.168.4.38/";
    private final String hubId = "001788fffe7107c8";
    private final String login = "UndoIYT7IxLAEiJsAnLN4nBwynr9xmSUgoYcywvh";
    private final String key = "EDE7190A863DB7F0CEC20E4E35BF95F1";

                //"success": {
                //"username": "UndoIYT7IxLAEiJsAnLN4nBwynr9xmSUgoYcywvh",
                //"clientkey": "EDE7190A863DB7F0CEC20E4E35BF95F1"

    public String getLogin() {
            return this.login;
        }

    public String getHubAddress() {
            return this.hubAddress;
        }

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
        LogManager.getLogger(LocationController.class).info("[ Hue light response : " + response.toString() + " ]");
    }
    private void setScene(final String scene) {
        final String restURL = "https://" + this.hubId + "/clip/v2/resource/scene/";
        final String body = "{\"recall\":{\"action\":\"active\", \"transitiontime\":50}}";

        RestClient restClient = RestClient.create();
        LogManager.getLogger(HueLightController.class).info("[ Turning scene : " + scene + ", " +  body + " ]");

        ResponseEntity<Void> response = restClient
                .put()
                .uri(restURL + "{sceneKey}", scene)
                .header("hue-application-key", this.login)
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        //.body(HueLightResponseBean.class);

        restClient.delete();
        LogManager.getLogger(LocationController.class).info("[ Hue scene response : " + response.toString() + " ]");
    }
    public void turnLightOff(final HueLightBean light) {
        final String body = "{\"on\":{\"on\":false}}";
        setLightState(light, body);
    }
    public void turnLightOn(final HueLightBean light) {
        final String body = "{\"on\":{\"on\":true}}";
        setLightState(light, body);
    }
    public void startMovie() {
        final String scene = "04a673ca-d640-4bbb-bd94-7368434a0f94";  // Hardcoded to Movie for now
        setScene(scene);
    }
    public void stopMovie() {
        final String scene = "f59f642e-89ed-45c2-9a53-2545dfe048b1";  // Hardcoded to Chill now
        setScene(scene);
    }
}
