package gregochr.controllers.dirigera;

import gregochr.services.sse.SseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

@RestController
@RequestMapping("/api/dirigera")
public class DirigeraStatusController {
    private static final Logger LOGGER = LogManager.getLogger(DirigeraStatusController.class);
    private static final int PORT = 8443;
    private static final int TIMEOUT_MS = 3000;

    @Value("${dirigera.hub-address}")
    private String hubAddress;

    @Autowired
    private SseService sseService;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return buildStatus();
    }

    @Scheduled(fixedRateString = "${dirigera.status-interval}")
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
                "reachable", reachable
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
