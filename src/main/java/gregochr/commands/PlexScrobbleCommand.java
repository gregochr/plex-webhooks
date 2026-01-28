package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

public class PlexScrobbleCommand extends PlexCommand  {
    public PlexScrobbleCommand(String event, String title, int duration) {
        super(event, title, duration);
    }

    public void execute(HueSceneController controller) {
        controller.stopMovieLightScene();
    }
}
