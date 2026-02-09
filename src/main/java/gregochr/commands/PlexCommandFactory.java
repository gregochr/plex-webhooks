package gregochr.commands;

import gregochr.services.plex.dto.PlexPayloadDto;

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

        if ("movie".equals(type) || "episode".equals(type)) {
            return PlexEvent.fromEvent(event)
                    .map(plexEvent -> new PlexCommand(plexEvent, title, duration, type))
                    .orElse(new PlexCommand(event, type));
        }
        return new PlexCommand(event, type);
    }
}
