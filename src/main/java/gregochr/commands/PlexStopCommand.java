package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

public class PlexStopCommand extends PlexCommand  {
    public PlexStopCommand(String event, String title, int duration) {
        super(event, title, duration);
    }

    public void execute(HueSceneController controller) {
        controller.stopMovieLightScene();
    }
}
