package gregochr.services.sse;

import com.fasterxml.jackson.annotation.JsonRawValue;
import gregochr.commands.PlexCommand;

import java.time.Instant;

public record SseEventDto(CommandDto command, @JsonRawValue String payload, String timestamp) {

    public SseEventDto(PlexCommand command, String rawPayload) {
        this(new CommandDto(command), rawPayload, Instant.now().toString());
    }

    public record CommandDto(String event, String title, int duration) {

        public CommandDto(PlexCommand command) {
            this(command.getEvent(), command.getTitle(), command.getDuration());
        }
    }
}
