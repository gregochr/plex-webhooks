package gregochr.services.config;

import org.springframework.stereotype.Component;

@Component
public class ConfigService {
    private volatile boolean hueIntegrationEnabled = true;

    public boolean isHueIntegrationEnabled() {
        return hueIntegrationEnabled;
    }

    public void setHueIntegrationEnabled(boolean enabled) {
        this.hueIntegrationEnabled = enabled;
    }
}
