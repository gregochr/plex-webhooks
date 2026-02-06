package gregochr.services.config;

import gregochr.services.sse.SseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final ConfigService configService;
    private final SseService sseService;

    public ConfigController(ConfigService configService, SseService sseService) {
        this.configService = configService;
        this.sseService = sseService;
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
}
