package gregochr.commands;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class PlexCommandConfigFactory {

    @Bean
    public PlexCommandConfig plexCommandConfig(
            @Value("${plex.movie-play-enabled:true}") boolean moviePlayEnabled,
            @Value("${plex.movie-resume-enabled:true}") boolean movieResumeEnabled,
            @Value("${plex.movie-stop-enabled:true}") boolean movieStopEnabled,
            @Value("${plex.movie-pause-enabled:true}") boolean moviePauseEnabled,
            @Value("${plex.movie-scrobble-enabled:true}") boolean movieScrobbleEnabled,
            @Value("${plex.episode-play-enabled:false}") boolean episodePlayEnabled,
            @Value("${plex.episode-resume-enabled:false}") boolean episodeResumeEnabled,
            @Value("${plex.episode-stop-enabled:false}") boolean episodeStopEnabled,
            @Value("${plex.episode-pause-enabled:false}") boolean episodePauseEnabled,
            @Value("${plex.episode-scrobble-enabled:false}") boolean episodeScrobbleEnabled,
            @Value("${plex.clip-play-enabled:false}") boolean clipPlayEnabled,
            @Value("${plex.clip-resume-enabled:false}") boolean clipResumeEnabled,
            @Value("${plex.clip-stop-enabled:false}") boolean clipStopEnabled,
            @Value("${plex.clip-pause-enabled:false}") boolean clipPauseEnabled,
            @Value("${plex.clip-scrobble-enabled:false}") boolean clipScrobbleEnabled,
            @Value("${plex.track-play-enabled:false}") boolean trackPlayEnabled,
            @Value("${plex.track-resume-enabled:false}") boolean trackResumeEnabled,
            @Value("${plex.track-stop-enabled:false}") boolean trackStopEnabled,
            @Value("${plex.track-pause-enabled:false}") boolean trackPauseEnabled,
            @Value("${plex.track-scrobble-enabled:false}") boolean trackScrobbleEnabled,
            @Value("${plex.photo-play-enabled:false}") boolean photoPlayEnabled,
            @Value("${plex.photo-resume-enabled:false}") boolean photoResumeEnabled,
            @Value("${plex.photo-stop-enabled:false}") boolean photoStopEnabled,
            @Value("${plex.photo-pause-enabled:false}") boolean photoPauseEnabled,
            @Value("${plex.photo-scrobble-enabled:false}") boolean photoScrobbleEnabled) {
        return new PlexCommandConfig(Map.of(
                PlexMediaType.MOVIE, new PlexMediaTypeConfig(moviePlayEnabled, movieResumeEnabled, movieStopEnabled, moviePauseEnabled, movieScrobbleEnabled),
                PlexMediaType.EPISODE, new PlexMediaTypeConfig(episodePlayEnabled, episodeResumeEnabled, episodeStopEnabled, episodePauseEnabled, episodeScrobbleEnabled),
                PlexMediaType.CLIP, new PlexMediaTypeConfig(clipPlayEnabled, clipResumeEnabled, clipStopEnabled, clipPauseEnabled, clipScrobbleEnabled),
                PlexMediaType.TRACK, new PlexMediaTypeConfig(trackPlayEnabled, trackResumeEnabled, trackStopEnabled, trackPauseEnabled, trackScrobbleEnabled),
                PlexMediaType.PHOTO, new PlexMediaTypeConfig(photoPlayEnabled, photoResumeEnabled, photoStopEnabled, photoPauseEnabled, photoScrobbleEnabled)
        ));
    }
}
