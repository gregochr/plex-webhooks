package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

public abstract class PlexCommand {
    protected String event;
    protected String title;

    public PlexCommand(String event, String title) {
        this.event = event;
        this.title  = title;
    }

    public String getEvent() {
        return this.event;
    }
    public String getTitle() {
        return this.title;
    }

    public String toString() {
        return "[ Event: " + this.event + ", " +
                "Title: " + this.title + " ]";
    }

    abstract public void execute(HueSceneController controller);
}
