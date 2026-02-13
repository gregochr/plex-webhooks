package gregochr.commands;

public record PlexMediaTypeConfig(
        boolean playEnabled,
        boolean resumeEnabled,
        boolean stopEnabled,
        boolean pauseEnabled,
        boolean scrobbleEnabled) {

    public boolean isEventEnabled(PlexEvent event) {
        return switch (event) {
            case PLAY -> playEnabled;
            case RESUME -> resumeEnabled;
            case STOP -> stopEnabled;
            case PAUSE -> pauseEnabled;
            case SCROBBLE -> scrobbleEnabled;
        };
    }
}
