package gregochr.commands;

import gregochr.services.plex.dto.PlexPayloadDto;

import java.util.Optional;

public class PlexCommandFactory {
    private final PlexPayloadDto plexPayload;

    public PlexCommandFactory(PlexPayloadDto plexPayload)  {
        this.plexPayload = plexPayload;
    }

    public PlexCommand getPlexCommand() {
        if (this.plexPayload.metadata() == null) {
            return new PlexCommand(this.plexPayload.event(), null);
        }
        String title = this.plexPayload.metadata().title();
        int duration = this.plexPayload.metadata().duration();
        String type = this.plexPayload.metadata().type();
        String event = this.plexPayload.event();

        Optional<PlexMediaType> mediaType = PlexMediaType.fromType(type);
        if (mediaType.isPresent()) {
            return PlexEvent.fromEvent(event)
                    .map(plexEvent -> new PlexCommand(plexEvent, title, duration, mediaType.get()))
                    .orElse(new PlexCommand(event, mediaType.get()));
        }
        return new PlexCommand(event, null);
    }
}
