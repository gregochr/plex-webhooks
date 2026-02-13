package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

public abstract class HueController {
    protected final String hubAddress;
    protected final String hubId;
    protected final String applicationKey;
    protected final RestClient restClient;
    private final ConfigService configService;

    protected HueController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService) {
        this.hubAddress = hubAddress;
        this.hubId = hubId;
        this.applicationKey = applicationKey;
        this.configService = configService;
        this.restClient = RestClient.builder()
                .baseUrl("https://%s/clip/v2/resource".formatted(hubId))
                .defaultHeader("hue-application-key", applicationKey)
                .build();
    }

    public boolean isHueIntegrationEnabled() {
        return configService.isHueIntegrationEnabled();
    }
}
