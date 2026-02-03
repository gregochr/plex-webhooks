package gregochr.controllers.hue;

import org.apache.logging.log4j.LogManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import org.springframework.stereotype.Component;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueSceneController extends HueController {
    private static final String FRONT_ROOM_MOVIE_SCENE = "04a673ca-d640-4bbb-bd94-7368434a0f94";
    private static final String FRONT_ROOM_CHILL_SCENE = "f59f642e-89ed-45c2-9a53-2545dfe048b1";
    private static final int SCENE_TRANSITION_DURATION_10_SECONDS = 10000;
    private static final String SCENE_URL = "https://{hubId}/clip/v2/resource/scene/{sceneId}";
    private static final String RECALL_ACTIVE_BODY = "{\"recall\":{\"action\":\"active\", \"duration\":" + SCENE_TRANSITION_DURATION_10_SECONDS + "}}";

    private boolean setScene(final String sceneId) {
        RestClient restClient = RestClient.create();
        LogManager.getLogger(HueSceneController.class).info("[ Turning sceneId : " + sceneId + " ]");

        ResponseEntity<Void> response = restClient
                .put()
                .uri(SCENE_URL, this.hubId, sceneId)
                .header("hue-application-key", this.login)
                .contentType(APPLICATION_JSON)
                .body(RECALL_ACTIVE_BODY)
                .retrieve()
                .toBodilessEntity();

        LogManager.getLogger(HueSceneController.class).info("[ Hue sceneId response : " + response + " ]");

        final HttpStatusCode responseStatusCode = response.getStatusCode();
        return responseStatusCode == HttpStatus.OK;
    }

    public boolean startMovieLightScene() {
        return setScene(FRONT_ROOM_MOVIE_SCENE);
    }

    public boolean stopMovieLightScene() {
        return setScene(FRONT_ROOM_CHILL_SCENE);
    }
}