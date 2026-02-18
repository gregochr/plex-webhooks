package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.services.ScheduledTaskManager;
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

@Service
public class SseService {
    private static final Logger LOGGER = LogManager.getLogger(SseService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long SSE_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours

    private final Map<String, SseClient> clients = new ConcurrentHashMap<>();
    private final ScheduledTaskManager scheduledTaskManager;

    public SseService(@Value("${sse.heartbeat-interval}") long intervalMs) {
        this.scheduledTaskManager = new ScheduledTaskManager(
                intervalMs, "sse.heartbeat-interval", "heartbeat", this::heartbeat, LOGGER);
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
