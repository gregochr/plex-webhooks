package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

public class PlexResumeCommand extends PlexCommand  {
    public PlexResumeCommand(String event, String  title) {
        super(event, title);
    }

    public void execute(HueSceneController controller){
        controller.startMovieLightScene();
    }
}
