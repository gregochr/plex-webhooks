package gregochr.controllers;

import gregochr.services.events.NowPlaying;
import gregochr.services.events.PlaybackEvent;
import gregochr.services.events.PlaybackHistory;
import gregochr.services.events.PlaybackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only views of what has played since CineLight started. */
@RestController
@RequestMapping("/api/playback")
public class PlaybackController {
    static final int DEFAULT_LIMIT = 20;

    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    /** The most recent media events, newest first. {@code limit} defaults to 20 and is clamped to 1..100. */
    @GetMapping("/history")
    public List<PlaybackEvent> history(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return playbackService.recent(Math.max(1, Math.min(limit, PlaybackHistory.CAPACITY)));
    }

    @GetMapping("/now")
    public NowPlaying now() {
        return playbackService.nowPlaying();
    }
}
