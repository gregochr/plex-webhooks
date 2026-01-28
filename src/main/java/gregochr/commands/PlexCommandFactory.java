package gregochr.commands;

import gregochr.services.plex.beans.PlexRootBean;

public class PlexCommandFactory {
    private final PlexRootBean plexRootBean;

    public PlexCommandFactory(PlexRootBean plexRootBean)  {
        this.plexRootBean = plexRootBean;
    }
    public PlexCommand getPlexCommand() {
        if (this.plexRootBean.getEvent().compareTo("media.play") == 0) {
            return new PlexPlayCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle(),
                    this.plexRootBean.getMetadata().getDuration());
        } else if (this.plexRootBean.getEvent().compareTo("media.resume") == 0) {
            return new PlexResumeCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle(),
                    this.plexRootBean.getMetadata().getDuration());
        } else if (this.plexRootBean.getEvent().compareTo("media.stop") == 0) {
            return new PlexStopCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle(),
                    this.plexRootBean.getMetadata().getDuration());
        } else if (this.plexRootBean.getEvent().compareTo("media.pause") == 0) {
            return new PlexPauseCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle(),
                    this.plexRootBean.getMetadata().getDuration());
        } else if (this.plexRootBean.getEvent().compareTo("media.scrobble") == 0) {
            return new PlexScrobbleCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle(),
                    this.plexRootBean.getMetadata().getDuration());
        }
        return null;
    }
}
