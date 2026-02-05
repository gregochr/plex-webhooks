package gregochr.services.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {
    private static final Logger LOGGER = LogManager.getLogger(SseService.class);
    private static final long SSE_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            LOGGER.debug("SSE client disconnected (completion)");
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            LOGGER.debug("SSE client disconnected (timeout)");
            emitters.remove(emitter);
        });

        emitter.onError(e -> {
            LOGGER.debug("SSE client disconnected (error): {}", e.getMessage());
            emitters.remove(emitter);
        });

        emitters.add(emitter);
        LOGGER.info("SSE client connected. Total clients: {}", emitters.size());

        return emitter;
    }

    public void broadcast(SseEventDto event) {
        LOGGER.debug("Broadcasting to {} clients", emitters.size());

        String json;
        try {
            json = new ObjectMapper().writeValueAsString(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                LOGGER.debug("Failed to send to client, removing: {}", e.getMessage());
                emitters.remove(emitter);
            }
        }
    }

    public int getClientCount() {
        return emitters.size();
    }
}
