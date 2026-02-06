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

        if ("movie".equals(this.plexPayload.getMetadata().getType()) ||
                "episode".equals(this.plexPayload.getMetadata().getType())) {
            if ("media.play".equals(this.plexPayload.getEvent())) {
                return new PlexPlayCommand(title, duration, type);
            } else if ("media.resume".equals(this.plexPayload.getEvent())) {
                return new PlexResumeCommand(title, duration, type);
            } else if ("media.stop".equals(this.plexPayload.getEvent())) {
                return new PlexStopCommand(title, duration, type);
            } else if ("media.pause".equals(this.plexPayload.getEvent())) {
                return new PlexPauseCommand(title, duration, type);
            } else if ("media.scrobble".equals(this.plexPayload.getEvent())) {
                return new PlexScrobbleCommand(title, duration, type);
            }
        }
        return new PlexNoOpCommand(this.plexPayload.getEvent(), this.plexPayload.getMetadata().getType());
    }
}
