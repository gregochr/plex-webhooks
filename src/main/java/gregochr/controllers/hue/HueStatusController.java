package gregochr.controllers.hue;

import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/hue")
public class HueStatusController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueStatusController.class);
    private static final RestClient REST_CLIENT = RestClient.create();

    @Autowired
    private SseService sseService;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return buildStatus();
    }

    @GetMapping("/test-connection")
    public Map<String, Object> testConnectionEndpoint() {
        boolean connected = testConnection();
        return Map.of(
                "connected", connected,
                "hubId", hubId
        );
    }

    @Scheduled(fixedRateString = "${hue.status-interval}")
    public void broadcastStatus() {
        if (sseService.getClientCount() == 0) {
            return;
        }
        sseService.broadcastHueStatus(buildStatus());
    }

    private Map<String, Object> buildStatus() {
        boolean connected = testConnection();
        return Map.of(
                "hubAddress", hubAddress,
                "hubId", hubId,
                "login", login,
                "key", key,
                "integrationEnabled", isHueIntegrationEnabled(),
                "connected", connected,
                "timestamp", Instant.now().toString()
        );
    }

    private boolean testConnection() {
        try {
            String url = "https://" + hubId + "/clip/v2/resource/bridge";
            LOGGER.debug("Testing Hue bridge connection: {}", url);

            ResponseEntity<String> response = REST_CLIENT
                    .get()
                    .uri(url)
                    .header("hue-application-key", login)
                    .retrieve()
                    .toEntity(String.class);

            boolean connected = response.getStatusCode().is2xxSuccessful();
            LOGGER.debug("Hue bridge connection test: {}", connected ? "SUCCESS" : "FAILED");
            return connected;
        } catch (RestClientException e) {
            LOGGER.warn("Hue bridge connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
