package gregochr.controllers.hue;

import gregochr.controllers.dto.hue.HueLightDto;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class HueLightController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueLightController.class);

    public HueLightController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService) {
        super(hubAddress, hubId, applicationKey, configService);
    }

    private void setLightState(final HueLightDto light, final String body) {
        LOGGER.info("[ Turning light: {}, {} ]", light.key(), body);

        ResponseEntity<Void> response = restClient
                .put()
                .uri("/light/{lightKey}", light.key())
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        LOGGER.info("[ Hue light response: {} ]", response);
    }

    private void turnLightOff(final HueLightDto light) {
        final String body = "{\"on\":{\"on\":false}}";
        setLightState(light, body);
    }

    private void turnLightOn(final HueLightDto light) {
        final String body = "{\"on\":{\"on\":true}}";
        setLightState(light, body);
    }
}
