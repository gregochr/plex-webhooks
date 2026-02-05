package gregochr.services.config;

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

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of("hueIntegrationEnabled", configService.isHueIntegrationEnabled());
    }

    @PutMapping("/hue-enabled")
    public Map<String, Object> setHueEnabled(@RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        configService.setHueIntegrationEnabled(enabled);
        return Map.of("hueIntegrationEnabled", configService.isHueIntegrationEnabled());
    }
}
