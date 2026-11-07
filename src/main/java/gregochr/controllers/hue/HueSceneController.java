package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class HueSceneController {
    private static final Logger LOGGER = LogManager.getLogger(HueSceneController.class);

    private final HueBridgeClient hueBridgeClient;
    private volatile String startScene;
    private volatile String stopScene;

    public HueSceneController(
            HueBridgeClient hueBridgeClient,
            @Value("${hue.start-scene}") String startScene,
            @Value("${hue.stop-scene}") String stopScene) {
        this.hueBridgeClient = hueBridgeClient;
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

    private Optional<String> setScene(final String sceneId) {
        return hueBridgeClient.activateScene(sceneId) ? Optional.of(sceneId) : Optional.empty();
    }

    /**
     * Activates the start scene. The volatile field is read once, so the id returned is the id activated even if a
     * hot reload lands mid-call.
     *
     * @return the id of the scene activated, or empty if the bridge did not activate it
     */
    public Optional<String> startMovieLightScene() {
        return setScene(startScene);
    }

    /** As {@link #startMovieLightScene()}, for the stop scene. */
    public Optional<String> stopMovieLightScene() {
        return setScene(stopScene);
    }

    public String getStartScene() {
        return startScene;
    }

    public String getStopScene() {
        return stopScene;
    }
}
