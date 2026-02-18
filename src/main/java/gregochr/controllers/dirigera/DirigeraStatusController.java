package gregochr.controllers.dirigera;

import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/dirigera")
public class DirigeraStatusController {
    private static final Logger LOGGER = LogManager.getLogger(DirigeraStatusController.class);
    private static final int PORT = 8443;
    private static final int TIMEOUT_MS = 3000;

    private final String hubAddress;
    private final SseService sseService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    public DirigeraStatusController(
            @Value("${dirigera.hub-address}") String hubAddress,
            SseService sseService,
            @Value("${dirigera.status-interval}") long intervalMs) {
        this.hubAddress = hubAddress;
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
        LOGGER.info("Scheduled dirigera status broadcast at interval: {} ms", intervalMs);
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        String value = event.getUpdatedProperties().get("dirigera.status-interval");
        if (value != null) {
            this.intervalMs = Long.parseLong(value);
            scheduleTask();
            LOGGER.info("Hot-reloaded dirigera.status-interval: {} ms", this.intervalMs);
        }
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return buildStatus();
    }

    public void broadcastStatus() {
        if (sseService.getClientCount() == 0) {
            return;
        }
        sseService.broadcastDirigeraStatus(buildStatus());
    }

    private Map<String, Object> buildStatus() {
        boolean reachable = testConnection();
        return Map.of(
                "hubAddress", hubAddress,
                "reachable", reachable,
                "timestamp", Instant.now().toString()
        );
    }

    private boolean testConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hubAddress, PORT), TIMEOUT_MS);
            LOGGER.debug("DIRIGERA hub connection test: SUCCESS ({}:{})", hubAddress, PORT);
            return true;
        } catch (IOException e) {
            LOGGER.warn("DIRIGERA hub connection test failed ({}:{}): {}", hubAddress, PORT, e.getMessage());
            return false;
        }
    }
}
