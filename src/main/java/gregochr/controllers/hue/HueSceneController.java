package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueSceneController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueSceneController.class);
    private static final String FRONT_ROOM_MOVIE_SCENE = "04a673ca-d640-4bbb-bd94-7368434a0f94";
    private static final String FRONT_ROOM_CHILL_SCENE = "f59f642e-89ed-45c2-9a53-2545dfe048b1";
    private static final int SCENE_TRANSITION_DURATION_10_SECONDS = 10000;
    private static final String RECALL_ACTIVE_BODY = "{\"recall\":{\"action\":\"active\", \"duration\":" + SCENE_TRANSITION_DURATION_10_SECONDS + "}}";

    public HueSceneController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService) {
        super(hubAddress, hubId, applicationKey, configService);
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
        return setScene(FRONT_ROOM_MOVIE_SCENE);
    }

    public boolean stopMovieLightScene() {
        return setScene(FRONT_ROOM_CHILL_SCENE);
    }
}
