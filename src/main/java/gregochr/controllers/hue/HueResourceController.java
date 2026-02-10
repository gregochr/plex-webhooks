package gregochr.controllers.hue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.controllers.dto.hue.HueResourceResponseDto;
import gregochr.services.config.ConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/hue")
public class HueResourceController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueResourceController.class);
    private static final RestClient REST_CLIENT = RestClient.create();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RESOURCE_URL = "https://{hubId}/clip/v2/resource";

    public HueResourceController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService) {
        super(hubAddress, hubId, applicationKey, configService);
    }

    @GetMapping(value = "/resources", produces = "application/json")
    public ResponseEntity<String> getResources() {
        try {
            LOGGER.info("[ Fetching all Hue resources from hub: {} ]", hubId);

            ResponseEntity<String> response = REST_CLIENT
                    .get()
                    .uri(RESOURCE_URL, hubId)
                    .header(HUE_APPLICATION_KEY_HEADER, applicationKey)
                    .retrieve()
                    .toEntity(String.class);

            LOGGER.info("[ Hue resource response status: {} ]", response.getStatusCode());
            return ResponseEntity.ok(response.getBody());
        } catch (RestClientException e) {
            LOGGER.warn("Hue resource fetch failed: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body("{\"error\": \"Hue bridge unreachable\"}");
        }
    }

    public HueResourceResponseDto getResourcesParsed() {
        ResponseEntity<String> response = getResources();
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(response.getBody(), HueResourceResponseDto.class);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to parse Hue resources: {}", e.getMessage());
            return null;
        }
    }
}
