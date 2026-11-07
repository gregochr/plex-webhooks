package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.ScheduledTaskManager;
import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Map;

@RestController
@RequestMapping("/api/hue")
public class HueStatusController {
    private static final Logger LOGGER = LogManager.getLogger(HueStatusController.class);

    private final HueBridgeClient hueBridgeClient;
    private final ConfigService configService;
    private final SseService sseService;
    private final ScheduledTaskManager scheduledTaskManager;
    private final Clock clock;

    @Value("${hue.hub-address}")
    private String hubAddress;

    public HueStatusController(
            HueBridgeClient hueBridgeClient,
            ConfigService configService,
            SseService sseService,
            @Value("${hue.status-interval}") long intervalMs,
            TaskScheduler taskScheduler,
            Clock clock) {
        this.hueBridgeClient = hueBridgeClient;
        this.configService = configService;
        this.sseService = sseService;
        this.clock = clock;
        this.scheduledTaskManager = new ScheduledTaskManager(taskScheduler, clock,
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
        boolean connected = hueBridgeClient.testConnection();
        return Map.of(
                "connected", connected,
                "hubId", hueBridgeClient.getHubId()
        );
    }

    public void broadcastStatus() {
        if (sseService.getClientCount() == 0) {
            return;
        }
        sseService.broadcastHueStatus(buildStatus());
    }

    private boolean isApplicationKeySet() {
        String key = hueBridgeClient.getAppKey();
        return key != null && !key.isBlank();
    }

    private Map<String, Object> buildStatus() {
        boolean connected = hueBridgeClient.testConnection();
        return Map.of(
                "hubAddress", hubAddress,
                "hubId", hueBridgeClient.getHubId(),
                "applicationKeySet", isApplicationKeySet(),
                "integrationEnabled", configService.isHueIntegrationEnabled(),
                "connected", connected,
                "timestamp", clock.instant().toString()
        );
    }
}
