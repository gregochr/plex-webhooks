package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

public class PlexPauseCommand  extends PlexCommand  {
    public PlexPauseCommand(String event, String title) {
        super(event, title);
    }

    public void execute(HueSceneController controller) {
        controller.stopMovieLightScene();
    }
}
