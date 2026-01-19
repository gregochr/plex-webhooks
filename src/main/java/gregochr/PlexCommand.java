package gregochr;

public class PlexCommand {
    private PlexEvent event;
    public enum PlexEvent {
        STOP,
        PLAY,
        PAUSE,
        RESUME,
        SCROBBLE
    }

    PlexCommand(PlexRootBean plexRootBean) {
        if (plexRootBean.event.compareTo("media.stop") == 0) {
            this.event = PlexEvent.STOP;
        } else if (plexRootBean.event.compareTo("media.play") == 0) {
            this.event = PlexEvent.PLAY;
        } else if (plexRootBean.event.compareTo("media.pause") == 0) {
            this.event = PlexEvent.PAUSE;
        } else if (plexRootBean.event.compareTo("media.resume") == 0) {
            this.event = PlexEvent.RESUME;
        } else if (plexRootBean.event.compareTo("media.scrobble") == 0) {
            this.event = PlexEvent.SCROBBLE;
        }
    }
    public String toString() {
        String command = "Command: ";
        if (this.event.compareTo(PlexEvent.STOP) == 0) {
            command += "STOP";
        } else if (this.event.compareTo(PlexEvent.PLAY) == 0) {
            command += "PLAY";
        } else if (this.event.compareTo(PlexEvent.PAUSE) == 0) {
            command += "PAUSE";
        } else if (this.event.compareTo(PlexEvent.RESUME) == 0) {
            command += "RESUME";
        } else if (this.event.compareTo(PlexEvent.SCROBBLE) == 0) {
            command += "SCROBBLE";
        }
        return command;
    }
}
