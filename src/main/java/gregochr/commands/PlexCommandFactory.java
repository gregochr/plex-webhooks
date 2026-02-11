package gregochr.commands;

import gregochr.services.plex.dto.PlexPayloadDto;

import java.util.Optional;

public class PlexCommandFactory {
    private final PlexPayloadDto plexPayload;

    public PlexCommandFactory(PlexPayloadDto plexPayload)  {
        this.plexPayload = plexPayload;
    }

    public PlexCommand getPlexCommand() {
        String title = this.plexPayload.getMetadata().getTitle();
        int duration = this.plexPayload.getMetadata().getDuration();
        String type = this.plexPayload.getMetadata().getType();
        String event = this.plexPayload.getEvent();

        Optional<PlexMediaType> mediaType = PlexMediaType.fromType(type);
        if (mediaType.isPresent()) {
            return PlexEvent.fromEvent(event)
                    .map(plexEvent -> new PlexCommand(plexEvent, title, duration, mediaType.get()))
                    .orElse(new PlexCommand(event, mediaType.get()));
        }
        return new PlexCommand(event, null);
    }
}
