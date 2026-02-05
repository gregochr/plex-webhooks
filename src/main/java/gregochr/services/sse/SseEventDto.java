package gregochr.services.sse;

import com.fasterxml.jackson.annotation.JsonRawValue;
import gregochr.commands.PlexCommand;

import java.time.Instant;

public class SseEventDto {
    private final CommandDto command;
    private final String payload;
    private final String timestamp;

    public SseEventDto(PlexCommand command, String rawPayload) {
        this.command = new CommandDto(command);
        this.payload = rawPayload;
        this.timestamp = Instant.now().toString();
    }

    public CommandDto getCommand() {
        return command;
    }

    @JsonRawValue
    public String getPayload() {
        return payload;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static class CommandDto {
        private final String event;
        private final String title;
        private final int duration;

        public CommandDto(PlexCommand command) {
            this.event = command.getEvent();
            this.title = command.getTitle();
            this.duration = command.getDuration();
        }

        public String getEvent() {
            return event;
        }

        public String getTitle() {
            return title;
        }

        public int getDuration() {
            return duration;
        }
    }
}
