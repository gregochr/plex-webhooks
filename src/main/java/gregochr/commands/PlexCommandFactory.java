package gregochr.commands;

import gregochr.services.plex.beans.PlexRootBean;

public class PlexCommandFactory {
    private final PlexRootBean plexRootBean;

    public PlexCommandFactory(PlexRootBean plexRootBean)  {
        this.plexRootBean = plexRootBean;
    }
    public PlexCommand getPlexCommand() {
        String title = this.plexRootBean.getMetadata().getTitle();
        int duration = this.plexRootBean.getMetadata().getDuration();

        if (this.plexRootBean.getEvent().compareTo("media.play") == 0) {
            return new PlexPlayCommand(title, duration);
        } else if (this.plexRootBean.getEvent().compareTo("media.resume") == 0) {
            return new PlexResumeCommand(title, duration);
        } else if (this.plexRootBean.getEvent().compareTo("media.stop") == 0) {
            return new PlexStopCommand(title, duration);
        } else if (this.plexRootBean.getEvent().compareTo("media.pause") == 0) {
            return new PlexPauseCommand(title, duration);
        } else if (this.plexRootBean.getEvent().compareTo("media.scrobble") == 0) {
            return new PlexScrobbleCommand(title, duration);
        }
        return null;
    }
}
