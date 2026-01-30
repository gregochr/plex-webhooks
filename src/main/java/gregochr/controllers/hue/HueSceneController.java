package gregochr.controllers.hue;

import org.apache.logging.log4j.LogManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HueSceneController extends HueController {
    private static final String SCENE_URL = "https://{hubId}/clip/v2/resource/scene/{sceneId}";
    private static final String RECALL_ACTIVE_BODY = "{\"recall\":{\"action\":\"active\"}}";

    private static final String FRONT_ROOM_MOVIE_SCENE = "04a673ca-d640-4bbb-bd94-7368434a0f94";
    private static final String FRONT_ROOM_CHILL_SCENE = "f59f642e-89ed-45c2-9a53-2545dfe048b1";

    private void setScene(final String sceneId) {
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
    }

    public void startMovieLightScene() {
        setScene(FRONT_ROOM_MOVIE_SCENE);
    }

    public void stopMovieLightScene() {
        setScene(FRONT_ROOM_CHILL_SCENE);
    }
}