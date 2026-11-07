package gregochr.controllers.dirigera;

import gregochr.services.ScheduledTaskManager;
import gregochr.services.config.PropertiesUpdatedEvent;
import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/dirigera")
public class DirigeraStatusController {
    private static final Logger LOGGER = LogManager.getLogger(DirigeraStatusController.class);
    private static final int PORT = 8443;
    private static final int TIMEOUT_MS = 3000;

    private final String hubAddress;
    private final SseService sseService;
    private final ScheduledTaskManager scheduledTaskManager;
    private final Clock clock;
    private final Supplier<Socket> socketFactory;

    @Autowired
    public DirigeraStatusController(
            @Value("${dirigera.hub-address}") String hubAddress,
            SseService sseService,
            @Value("${dirigera.status-interval}") long intervalMs,
            TaskScheduler taskScheduler,
            Clock clock) {
        this(hubAddress, sseService, intervalMs, taskScheduler, clock, Socket::new);
    }

    /** Lets tests supply the socket, so the connection check can be exercised without a network. */
    DirigeraStatusController(String hubAddress, SseService sseService, long intervalMs, TaskScheduler taskScheduler,
            Clock clock, Supplier<Socket> socketFactory) {
        this.hubAddress = hubAddress;
        this.sseService = sseService;
        this.clock = clock;
        this.socketFactory = socketFactory;
        this.scheduledTaskManager = new ScheduledTaskManager(taskScheduler, clock,
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
                "timestamp", clock.instant().toString()
        );
    }

    boolean testConnection() {
        try (Socket socket = socketFactory.get()) {
            socket.connect(new InetSocketAddress(hubAddress, PORT), TIMEOUT_MS);
            LOGGER.debug("DIRIGERA hub connection test: SUCCESS ({}:{})", hubAddress, PORT);
            return true;
        } catch (IOException e) {
            LOGGER.warn("DIRIGERA hub connection test failed ({}:{}): {}", hubAddress, PORT, e.getMessage());
            return false;
        }
    }
}
