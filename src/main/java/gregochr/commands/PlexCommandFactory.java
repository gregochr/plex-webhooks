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

        if ("media.play".equals(this.plexPayload.getEvent())) {
            return new PlexPlayCommand(title, duration);
        } else if ("media.resume".equals(this.plexPayload.getEvent())) {
            return new PlexResumeCommand(title, duration);
        } else if ("media.stop".equals(this.plexPayload.getEvent())) {
            return new PlexStopCommand(title, duration);
        } else if ("media.pause".equals(this.plexPayload.getEvent())) {
            return new PlexPauseCommand(title, duration);
        } else if ("media.scrobble".equals(this.plexPayload.getEvent())) {
            return new PlexScrobbleCommand(title, duration);
        }
        return new PlexNoOpCommand(this.plexPayload.getEvent());
    }
}
