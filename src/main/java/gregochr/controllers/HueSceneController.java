package gregochr.controllers;

import gregochr.commands.PlexCommand;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HueSceneController extends HueController {
    private void setScene(final String sceneId) {
        final String restURL = "https://" + this.hubId + "/clip/v2/resource/sceneId/";
        final String body = "{\"recall\":{\"action\":\"active\", \"transitiontime\":50}}";

        RestClient restClient = RestClient.create();
        LogManager.getLogger(HueSceneController.class).info("[ Turning sceneId : " + sceneId + ", " +  body + " ]");

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
        LogManager.getLogger(LocationController.class).info("[ Hue sceneId response : " + response.toString() + " ]");
    }

    private void startMovieLightScene() {
        final String scene = "04a673ca-d640-4bbb-bd94-7368434a0f94";  // Hardcoded to Movie for now
        setScene(scene);
    }
    private void stopMovieLightScene() {
        final String scene = "f59f642e-89ed-45c2-9a53-2545dfe048b1";  // Hardcoded to Chill now
        setScene(scene);
    }

    public void execute(PlexCommand command) {
        if (command.getCommand() == PlexCommand.PlexCommandEnum.PLAY ||
            command.getCommand() == PlexCommand.PlexCommandEnum.RESUME) {
            startMovieLightScene();
        } else {
            stopMovieLightScene();
        }
    }
}
