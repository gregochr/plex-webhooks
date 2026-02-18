package gregochr.controllers.dirigera;

import gregochr.services.ScheduledTaskManager;
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

@RestController
@RequestMapping("/api/dirigera")
public class DirigeraStatusController {
    private static final Logger LOGGER = LogManager.getLogger(DirigeraStatusController.class);
    private static final int PORT = 8443;
    private static final int TIMEOUT_MS = 3000;

    private final String hubAddress;
    private final SseService sseService;
    private final ScheduledTaskManager scheduledTaskManager;

    public DirigeraStatusController(
            @Value("${dirigera.hub-address}") String hubAddress,
            SseService sseService,
            @Value("${dirigera.status-interval}") long intervalMs) {
        this.hubAddress = hubAddress;
        this.sseService = sseService;
        this.scheduledTaskManager = new ScheduledTaskManager(
                intervalMs, "dirigera.status-interval", "dirigera status broadcast", this::broadcastStatus, LOGGER);
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

    boolean testConnection() {
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
