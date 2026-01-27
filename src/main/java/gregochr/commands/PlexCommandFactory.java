package gregochr.commands;

import gregochr.services.plex.beans.PlexRootBean;

public class PlexCommandFactory {
    private final PlexRootBean plexRootBean;

    public PlexCommandFactory(PlexRootBean plexRootBean)  {
        this.plexRootBean = plexRootBean;
    }
    public PlexCommand getPlexCommand() {
        if (this.plexRootBean.getEvent().compareTo("play") == 0) {
            return new PlexPlayCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle());
        } else if (this.plexRootBean.getEvent().compareTo("resume") == 0) {
            return new PlexResumeCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle());
        } else if (this.plexRootBean.getEvent().compareTo("stop") == 0) {
            return new PlexStopCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle());
        } else if (this.plexRootBean.getEvent().compareTo("pause") == 0) {
            return new PlexPauseCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle());
        } else if (this.plexRootBean.getEvent().compareTo("scrobble") == 0) {
            return new PlexScrobbleCommand(this.plexRootBean.getEvent(),
                    this.plexRootBean.getMetadata().getTitle());
        }
        return null;
    }
}
