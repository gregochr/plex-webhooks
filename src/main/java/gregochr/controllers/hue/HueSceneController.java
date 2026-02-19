package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    private boolean setScene(final String sceneId) {
        return hueBridgeClient.activateScene(sceneId);
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
