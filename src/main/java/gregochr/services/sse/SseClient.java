package gregochr.services.sse;

import org.apache.logging.log4j.LogManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

public record SseClient(
        String clientId,
        SseEmitter emitter,
        String userAgent,
        Instant connectedAt
) {
    public String getBrowserInfo() {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        } else {
            LogManager.getLogger(SseClient.class).info("[ User agent: " + userAgent + " ]");
        }

        String browser = "Unknown";
        String os = "Unknown";

        // Detect browser
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome")) {
            browser = "Safari";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        }

        // Detect OS
        if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            os = "iOS";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        }

        return browser + " on " + os;
    }
}
