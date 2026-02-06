package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    public SseEmitter subscribe(String clientId, String userAgent) {
        // Close any existing connection for this client (reconnect scenario)
        SseClient existingClient = clients.remove(clientId);
        if (existingClient != null) {
            LOGGER.info("SSE client reconnecting: {} ({})", clientId, existingClient.getBrowserInfo());
            existingClient.emitter().complete();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            // Only log if client still exists (not a replacement)
            if (clients.remove(clientId) != null) {
                LOGGER.info("SSE client disconnected (completion): {}", clientId);
            }
        });

        emitter.onTimeout(() -> {
            if (clients.remove(clientId) != null) {
                LOGGER.info("SSE client disconnected (timeout): {}", clientId);
            }
        });

        emitter.onError(e -> {
            if (clients.remove(clientId) != null) {
                LOGGER.info("SSE client disconnected (error): {} - {}", clientId, e.getMessage());
            }
        });

        SseClient client = new SseClient(clientId, emitter, userAgent, Instant.now());
        clients.put(clientId, client);
        LOGGER.info("SSE client connected: {} ({}). Total clients: {}", clientId, client.getBrowserInfo(), clients.size());

        // Send initial event to trigger browser's onopen
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            LOGGER.debug("Failed to send initial event: {}", e.getMessage());
        }

        return emitter;
    }

    public void broadcast(SseEventDto event) {
        broadcastEvent("message", event);
    }

    public void broadcastConfig(Object config) {
        broadcastEvent("config", config);
    }

    private void broadcastEvent(String eventName, Object data) {
        LOGGER.debug("Broadcasting '{}' event to {} clients", eventName, clients.size());

        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {}", e.getMessage());
            return;
        }

        clients.forEach((clientId, client) -> {
            try {
                client.emitter().send(SseEmitter.event()
                        .name(eventName)
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                LOGGER.info("Failed to send to client {}, removing: {}", clientId, e.getMessage());
                clients.remove(clientId);
            }
        });
    }

    public int getClientCount() {
        return clients.size();
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void heartbeat() {
        if (clients.isEmpty()) {
            LOGGER.info("No clients to receive heartbeats");
            return;
        }
        LOGGER.info("Sending heartbeat to {} clients", clients.size());
        clients.forEach((clientId, client) -> {
            try {
                LOGGER.info("Sending heartbeat to {} ({})", clientId, client.getBrowserInfo());
                client.emitter().send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{}", MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                LOGGER.info("Heartbeat failed for client {}, removing: {}", clientId, e.getMessage());
                clients.remove(clientId);
            }
        });
    }
}
