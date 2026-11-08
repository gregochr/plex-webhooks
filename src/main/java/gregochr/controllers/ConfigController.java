package gregochr.controllers;

import gregochr.services.config.ClientConfig;
import gregochr.services.config.ClientConfigService;
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
import java.util.Set;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;
    private final SseService sseService;
    private final ConfigurationViewService configurationViewService;
    private final ClientConfigService clientConfigService;

    public ConfigController(ConfigService configService, SseService sseService,
                            ConfigurationViewService configurationViewService,
                            ClientConfigService clientConfigService) {
        this.configService = configService;
        this.sseService = sseService;
        this.configurationViewService = configurationViewService;
        this.clientConfigService = clientConfigService;
    }

    /** The dashboard configuration; the same shape the {@code config} SSE event carries. Credential-free. */
    @GetMapping
    public ClientConfig getConfig() {
        return clientConfigService.snapshot();
    }

    @PutMapping("/hue-enabled")
    public ClientConfig setHueEnabled(@RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        configService.setHueIntegrationEnabled(enabled);

        return broadcastConfig();
    }

    private ClientConfig broadcastConfig() {
        ClientConfig config = clientConfigService.snapshot();
        sseService.broadcastConfig(config);
        return config;
    }

    @PutMapping("/plex-commands/{mediaType}")
    public ResponseEntity<Map<String, Boolean>> savePlexCommands(
            @PathVariable String mediaType,
            @RequestBody Map<String, Boolean> body) throws IOException {
        if (!configurationViewService.isValidMediaType(mediaType)) {
            return ResponseEntity.notFound().build();
        }

        Set<String> allowedKeys = ConfigurationViewService.plexCommandKeys(mediaType);
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : body.entrySet()) {
            if (allowedKeys.contains(entry.getKey())) {
                updates.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        configurationViewService.saveProperties(updates);
        broadcastConfig();

        return ResponseEntity.ok(body);
    }

    @PutMapping("/hue-scene")
    public ResponseEntity<Map<String, String>> saveHueScene(@RequestBody Map<String, String> body) throws IOException {
        String key = body.get("key");
        String sceneId = body.get("sceneId");
        if (key == null || sceneId == null || !configurationViewService.isValidSceneKey(key)
                || !configurationViewService.isValidSceneId(sceneId)) {
            return ResponseEntity.badRequest().build();
        }

        configurationViewService.saveProperties(Map.of(key, sceneId));
        broadcastConfig();

        return ResponseEntity.ok(Map.of("key", key, "sceneId", sceneId));
    }

    @PutMapping("/properties")
    public ResponseEntity<Map<String, String>> saveProperties(
            @RequestBody Map<String, String> body) throws IOException {
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (configurationViewService.getValidPropertyKeys().contains(entry.getKey())) {
                if (!configurationViewService.isValidPropertyValue(entry.getKey(), entry.getValue())) {
                    return ResponseEntity.badRequest().build();
                }
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
