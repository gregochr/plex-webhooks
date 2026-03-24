package gregochr.controllers;

import gregochr.services.config.ConfigService;
import gregochr.services.config.ConfigurationViewService;
import gregochr.services.sse.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;
    private final SseService sseService;
    private final ConfigurationViewService configurationViewService;

    public ConfigController(ConfigService configService, SseService sseService,
                            ConfigurationViewService configurationViewService) {
        this.configService = configService;
        this.sseService = sseService;
        this.configurationViewService = configurationViewService;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of("hueIntegrationEnabled", configService.isHueIntegrationEnabled());
    }

    @PutMapping("/hue-enabled")
    public Map<String, Object> setHueEnabled(@RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        configService.setHueIntegrationEnabled(enabled);

        Map<String, Object> config = Map.of("hueIntegrationEnabled", configService.isHueIntegrationEnabled());
        sseService.broadcastConfig(config);

        return config;
    }

    @GetMapping("/plex-commands/{mediaType}")
    public ResponseEntity<Map<String, Boolean>> getPlexCommands(@PathVariable String mediaType) {
        if (!configurationViewService.isValidMediaType(mediaType)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Boolean> properties = configurationViewService.getPlexProperties(mediaType);
        return ResponseEntity.ok(properties);
    }

    @PutMapping("/plex-commands/{mediaType}")
    public ResponseEntity<Map<String, Boolean>> savePlexCommands(
            @PathVariable String mediaType,
            @RequestBody Map<String, Boolean> body) throws IOException {
        if (!configurationViewService.isValidMediaType(mediaType)) {
            return ResponseEntity.notFound().build();
        }

        String prefix = "plex." + mediaType + "-";
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : body.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                updates.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        configurationViewService.saveProperties(updates);

        return ResponseEntity.ok(body);
    }

    @GetMapping("/hue-scenes")
    public Map<String, String> getHueScenes() {
        return configurationViewService.getSceneProperties();
    }

    @PutMapping("/hue-scene")
    public ResponseEntity<Map<String, String>> saveHueScene(@RequestBody Map<String, String> body) throws IOException {
        String key = body.get("key");
        String sceneId = body.get("sceneId");
        if (key == null || sceneId == null || !configurationViewService.isValidSceneKey(key)) {
            return ResponseEntity.badRequest().build();
        }

        configurationViewService.saveProperties(Map.of(key, sceneId));

        return ResponseEntity.ok(Map.of("key", key, "sceneId", sceneId));
    }

    @PutMapping("/properties")
    public ResponseEntity<Map<String, String>> saveProperties(
            @RequestBody Map<String, String> body) throws IOException {
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (configurationViewService.getValidPropertyKeys().contains(entry.getKey())) {
                updates.put(entry.getKey(), entry.getValue());
            }
        }

        if (updates.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        configurationViewService.saveProperties(updates);

        return ResponseEntity.ok(updates);
    }
}
