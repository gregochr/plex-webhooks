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

        if ("media.play".equals(this.plexRootBean.getEvent())) {
            return new PlexPlayCommand(title, duration);
        } else if ("media.resume".equals(this.plexRootBean.getEvent())) {
            return new PlexResumeCommand(title, duration);
        } else if ("media.stop".equals(this.plexRootBean.getEvent())) {
            return new PlexStopCommand(title, duration);
        } else if ("media.pause".equals(this.plexRootBean.getEvent())) {
            return new PlexPauseCommand(title, duration);
        } else if ("media.scrobble".equals(this.plexRootBean.getEvent())) {
            return new PlexScrobbleCommand(title, duration);
        }
        return new PlexNoOpCommand(this.plexRootBean.getEvent());
    }
}
