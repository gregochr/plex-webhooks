package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HueConfig {

    @Bean
    public HueBridgeClient hueBridgeClient(
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String appKey) {
        return HueBridgeClient.create(hubId, appKey);
    }
}
