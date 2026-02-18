package gregochr.controllers.hue;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/hue")
public class HueStatusController extends HueController {
    private static final Logger LOGGER = LogManager.getLogger(HueStatusController.class);

    private final SseService sseService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    public HueStatusController(
            @Value("${hue.hub-address}") String hubAddress,
            @Value("${hue.hub-id}") String hubId,
            @Value("${hue.application-key}") String applicationKey,
            ConfigService configService,
            SseService sseService,
            @Value("${hue.status-interval}") long intervalMs) {
        super(hubAddress, hubId, applicationKey, configService);
        this.sseService = sseService;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void init() {
        scheduleTask();
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }

    private void scheduleTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduledTask = scheduler.scheduleAtFixedRate(this::broadcastStatus, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("Scheduled hue status broadcast at interval: {} ms", intervalMs);
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        String value = event.getUpdatedProperties().get("hue.status-interval");
        if (value != null) {
            this.intervalMs = Long.parseLong(value);
            scheduleTask();
            LOGGER.info("Hot-reloaded hue.status-interval: {} ms", this.intervalMs);
        }
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

    private boolean testConnection() {
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
