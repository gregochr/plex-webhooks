package gregochr.controllers.hue;

import gregochr.services.ScheduledTaskManager;
import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/hue")
public class HueStatusController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueStatusController.class);

    private final SseService sseService;
    private final ScheduledTaskManager scheduledTaskManager;

    public HueStatusController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService,
            SseService sseService,
            @Value("${hue.status-interval}") long intervalMs) {
        super(hubAddress, hubId, applicationKey, configService);
        this.sseService = sseService;
        this.scheduledTaskManager = new ScheduledTaskManager(
                intervalMs, "hue.status-interval", "hue status broadcast", this::broadcastStatus, LOGGER);
    }

    @PostConstruct
    public void init() {
        scheduledTaskManager.start();
    }

    @PreDestroy
    public void destroy() {
        scheduledTaskManager.shutdown();
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        scheduledTaskManager.onPropertiesUpdated(event);
    }

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
                "applicationKey", applicationKey,
                "integrationEnabled", isHueIntegrationEnabled(),
                "connected", connected,
                "timestamp", Instant.now().toString()
        );
    }

    boolean testConnection() {
        try {
            LOGGER.debug("Testing Hue bridge connection: {}", hubId);

            ResponseEntity<String> response = restClient
                    .get()
                    .uri("/bridge")
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
