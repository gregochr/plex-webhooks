package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import org.springframework.beans.factory.annotation.Value;

public abstract class HueController {
    protected static final String HUE_APPLICATION_KEY_HEADER = "hue-application-key";

    protected final String hubAddress;
    protected final String hubId;
    protected final String applicationKey;
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
    }

    public boolean isHueIntegrationEnabled() {
        return configService.isHueIntegrationEnabled();
    }
}
