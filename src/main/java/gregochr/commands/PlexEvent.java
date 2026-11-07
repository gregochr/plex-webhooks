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
    private final Function<HueSceneController, Optional<String>> action;

    PlexEvent(String event, Function<HueSceneController, Optional<String>> action) {
        this.event = event;
        this.action = action;
    }

    public String getEvent() {
        return event;
    }

    /** Runs the scene change; returns the id of the scene activated, or empty if the bridge did not activate it. */
    public Optional<String> execute(HueSceneController hueSceneController) {
        return action.apply(hueSceneController);
    }

    public static Optional<PlexEvent> fromEvent(String event) {
        return Arrays.stream(values())
                .filter(e -> e.event.equals(event))
                .findFirst();
    }
}
