package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.services.config.PropertiesUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SseService {
    private static final Logger LOGGER = LogManager.getLogger(SseService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long SSE_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours

    private final Map<String, SseClient> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private long intervalMs;

    public SseService(@Value("${sse.heartbeat-interval}") long intervalMs) {
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
        scheduledTask = scheduler.scheduleAtFixedRate(this::heartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("Scheduled heartbeat at interval: {} ms", intervalMs);
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        String value = event.getUpdatedProperties().get("sse.heartbeat-interval");
        if (value != null) {
            this.intervalMs = Long.parseLong(value);
            scheduleTask();
            LOGGER.info("Hot-reloaded sse.heartbeat-interval: {} ms", this.intervalMs);
        }
    }

    public SseEmitter subscribe(String clientId, String userAgent, String ipAddress) {
        // Close any existing connection for this client (reconnect scenario)
        SseClient existingClient = this.clients.remove(clientId);
        if (existingClient != null) {
            LOGGER.info("SSE client reconnecting: {} ({}) from {}", clientId, existingClient.getBrowserInfo(), existingClient.ipAddress());
            existingClient.emitter().complete();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> removeIfSameEmitter(clientId, emitter, "completion"));

        emitter.onTimeout(() -> removeIfSameEmitter(clientId, emitter, "timeout"));

        emitter.onError(e -> removeIfSameEmitter(clientId, emitter, "error: " + e.getMessage()));

        SseClient client = new SseClient(clientId, emitter, userAgent, ipAddress, Instant.now());
        clients.put(clientId, client);
        LOGGER.info("SSE client connected: {} ({}) from {}. Total clients: {}", clientId, client.getBrowserInfo(), ipAddress, clients.size());

        // Send initial event to trigger browser's onopen
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            LOGGER.info("Failed to send initial event: {}", e.getMessage());
        }

        return emitter;
    }

    public void broadcast(SseEventDto event) {
        broadcastEvent("message", event);
    }

    public void broadcastConfig(Object config) {
        broadcastEvent("config", config);
    }

    public void broadcastHueStatus(Object status) {
        broadcastEvent("hue-status", status);
    }

    public void broadcastDirigeraStatus(Object status) {
        broadcastEvent("dirigera-status", status);
    }

    private void broadcastEvent(String eventName, Object data) {
        LOGGER.info("Broadcasting '{}' event to {} clients", eventName, this.clients.size());

        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            LOGGER.info("Failed to serialize event: {}", e.getMessage());
            return;
        }

        this.clients.forEach((clientId, client) -> {
            try {
                client.emitter().send(SseEmitter.event()
                        .name(eventName)
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                LOGGER.info("Failed to send to client {}, removing: {}", clientId, e.getMessage());
                this.clients.remove(clientId);
            }
        });
    }

    public int getClientCount() {
        return this.clients.size();
    }

    void removeIfSameEmitter(String clientId, SseEmitter emitter, String reason) {
        clients.computeIfPresent(clientId, (id, client) -> {
            if (client.emitter() == emitter) {
                LOGGER.info("SSE client disconnected ({}): {}", reason, clientId);
                return null;
            }
            return client;
        });
    }

    public void heartbeat() {
        if (this.clients.isEmpty()) {
            LOGGER.info("No clients to receive heartbeats");
            return;
        }
        LOGGER.info("Sending heartbeat to {} clients", this.clients.size());
        this.clients.forEach((clientId, client) -> {
            try {
                LOGGER.info("Sending heartbeat to {} ({})", clientId, client.toString());
                client.emitter().send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{}", MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                LOGGER.info("Heartbeat failed for client {}, removing: {}", clientId, e.getMessage());
                this.clients.remove(clientId);
            }
        });
    }
}
