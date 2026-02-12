package gregochr.commands;

import gregochr.controllers.hue.HueSceneController;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public enum PlexEvent {
    PLAY("media.play", HueSceneController::startMovieLightScene),
    RESUME("media.resume", HueSceneController::startMovieLightScene),
    STOP("media.stop", HueSceneController::stopMovieLightScene),
    PAUSE("media.pause", HueSceneController::stopMovieLightScene),
    SCROBBLE("media.scrobble", HueSceneController::stopMovieLightScene);

    private final String event;
    private final Function<HueSceneController, Boolean> action;

    PlexEvent(String event, Function<HueSceneController, Boolean> action) {
        this.event = event;
        this.action = action;
    }

    public String getEvent() {
        return event;
    }

    public boolean execute(HueSceneController hueSceneController) {
        return action.apply(hueSceneController);
    }

    public static Optional<PlexEvent> fromEvent(String event) {
        return Arrays.stream(values())
                .filter(e -> e.event.equals(event))
                .findFirst();
    }
}
