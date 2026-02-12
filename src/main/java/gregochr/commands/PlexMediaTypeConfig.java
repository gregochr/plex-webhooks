package gregochr.commands;

public class PlexMediaTypeConfig {
    private final boolean playEnabled;
    private final boolean resumeEnabled;
    private final boolean stopEnabled;
    private final boolean pauseEnabled;
    private final boolean scrobbleEnabled;

    public PlexMediaTypeConfig(boolean playEnabled, boolean resumeEnabled, boolean stopEnabled, boolean pauseEnabled, boolean scrobbleEnabled) {
        this.playEnabled = playEnabled;
        this.resumeEnabled = resumeEnabled;
        this.stopEnabled = stopEnabled;
        this.pauseEnabled = pauseEnabled;
        this.scrobbleEnabled = scrobbleEnabled;
    }

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
