package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueSceneController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueSceneController.class);
    private static final int SCENE_TRANSITION_DURATION_10_SECONDS = 10000;
    private static final String RECALL_ACTIVE_BODY = "{\"recall\":{\"action\":\"active\", \"duration\":" + SCENE_TRANSITION_DURATION_10_SECONDS + "}}";

    private String startScene;
    private String stopScene;

    public HueSceneController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            @Value("${hue.start-scene}") String startScene,
            @Value("${hue.stop-scene}") String stopScene,
            ConfigService configService) {
        super(hubAddress, hubId, applicationKey, configService);
        this.startScene = startScene;
        this.stopScene = stopScene;
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        Map<String, String> updates = event.getUpdatedProperties();
        if (updates.containsKey("hue.start-scene")) {
            this.startScene = updates.get("hue.start-scene");
            LOGGER.info("Hot-reloaded hue.start-scene: {}", this.startScene);
        }
        if (updates.containsKey("hue.stop-scene")) {
            this.stopScene = updates.get("hue.stop-scene");
            LOGGER.info("Hot-reloaded hue.stop-scene: {}", this.stopScene);
        }
    }

    private boolean setScene(final String sceneId) {
        try {
            LOGGER.info("[ Turning sceneId: {} ]", sceneId);

            ResponseEntity<Void> response = restClient
                    .put()
                    .uri("/scene/{sceneId}", sceneId)
                    .contentType(APPLICATION_JSON)
                    .body(RECALL_ACTIVE_BODY)
                    .retrieve()
                    .toBodilessEntity();

            LOGGER.info("[ Hue sceneId response: {} ]", response);

            final HttpStatusCode responseStatusCode = response.getStatusCode();
            return responseStatusCode == HttpStatus.OK;
        } catch (RestClientException e) {
            LOGGER.warn("Hue scene activation failed (sceneId: {}): {}", sceneId, e.getMessage());
            return false;
        }
    }

    public boolean startMovieLightScene() {
        return setScene(startScene);
    }

    public boolean stopMovieLightScene() {
        return setScene(stopScene);
    }

    String getStartScene() {
        return startScene;
    }

    String getStopScene() {
        return stopScene;
    }
}
