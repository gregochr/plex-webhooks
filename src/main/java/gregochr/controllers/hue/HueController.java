package gregochr.controllers.hue;

import gregochr.services.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public abstract class HueController {
    @Value("${hue.hub-address}")
    protected String hubAddress;

    @Value("${hue.hub-id}")
    protected String hubId;

    @Value("${hue.login}")
    protected String login;

    @Value("${hue.key}")
    protected String key;

    @Autowired
    protected RestClient restClient;

    @Autowired
    private ConfigService configService;

    public boolean isHueIntegrationEnabled() {
        return configService.isHueIntegrationEnabled();
    }
}
