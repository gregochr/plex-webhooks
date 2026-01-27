package gregochr.commands;

import gregochr.beans.PlexRootBean;
import gregochr.controllers.HueController;
import gregochr.controllers.HueSceneController;

import java.util.ArrayList;

public class PlexCommand {
    private PlexCommandEnum command;

    public enum PlexCommandEnum {
        STOP,
        PLAY,
        PAUSE,
        RESUME,
        SCROBBLE
    }

    public PlexCommandEnum getCommand() {
        return this.command;
    }
    public PlexCommand(PlexRootBean plexRootBean) {
        if (plexRootBean.event.compareTo("media.stop") == 0) {
            this.command = PlexCommandEnum.STOP;
        } else if (plexRootBean.event.compareTo("media.play") == 0) {
            this.command = PlexCommandEnum.PLAY;
        } else if (plexRootBean.event.compareTo("media.pause") == 0) {
            this.command = PlexCommandEnum.PAUSE;
        } else if (plexRootBean.event.compareTo("media.resume") == 0) {
            this.command = PlexCommandEnum.RESUME;
        } else if (plexRootBean.event.compareTo("media.scrobble") == 0) {
            this.command = PlexCommandEnum.SCROBBLE;
        }
    }
    public String toString() {
        String command = "Command: ";
        if (this.command.compareTo(PlexCommandEnum.STOP) == 0) {
            command += "STOP";
        } else if (this.command.compareTo(PlexCommandEnum.PLAY) == 0) {
            command += "PLAY";
        } else if (this.command.compareTo(PlexCommandEnum.PAUSE) == 0) {
            command += "PAUSE";
        } else if (this.command.compareTo(PlexCommandEnum.RESUME) == 0) {
            command += "RESUME";
        } else if (this.command.compareTo(PlexCommandEnum.SCROBBLE) == 0) {
            command += "SCROBBLE";
        }
        return command;
    }

    public void execute(HueSceneController controller){
        controller.execute(this);
    }
}
