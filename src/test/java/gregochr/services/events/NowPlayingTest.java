package gregochr.services.events;

import gregochr.commands.PlexEvent;
import gregochr.commands.PlexOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Now Playing State Machine Tests")
public class NowPlayingTest {

    private static final String T1 = "2024-03-15T12:00:00Z";
    private static final String T2 = "2024-03-15T12:05:00Z";
    private static final String T3 = "2024-03-15T12:10:00Z";

    private static PlaybackEvent event(String name, String title, String at, PlexOutcome.Kind outcome,
            String sceneId) {
        return new PlaybackEvent(1, at, name, "episode", title, "Slow Horses", 4, 2, 2025, "TV", outcome, sceneId,
                "plex", "{}", new PlaybackEvent.Command(name, title, 0));
    }

    private static NowPlaying apply(NowPlaying now, PlexEvent plexEvent, String title, String at,
            PlexOutcome.Kind outcome, String sceneId) {
        return now.apply(event(plexEvent.getEvent(), title, at, outcome, sceneId), plexEvent);
    }

    @DisplayName("Before any event the state is Idle with nothing else set")
    @Test
    public void startsIdle() {
        NowPlaying idle = NowPlaying.idle();

        assertEquals(NowPlaying.State.IDLE, idle.state());
        assertNull(idle.since());
        assertNull(idle.title());
        assertNull(idle.sceneId());
    }

    @DisplayName("Play enters Playing, with the title fields, player and the scene it applied")
    @Test
    public void playEntersPlaying() {
        NowPlaying now = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1, PlexOutcome.Kind.SCENE_APPLIED,
                "start");

        assertEquals(new NowPlaying(NowPlaying.State.PLAYING, T1, "episode", "Cold Open", "Slow Horses", 4, 2, 2025,
                "TV", "start"), now);
    }

    @DisplayName("Pause enters Paused and stop enters Stopped, each stamping since")
    @Test
    public void pauseAndStop() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");

        NowPlaying paused = apply(playing, PlexEvent.PAUSE, "Cold Open", T2, PlexOutcome.Kind.SCENE_APPLIED, "stop");
        NowPlaying stopped = apply(paused, PlexEvent.STOP, "Cold Open", T3, PlexOutcome.Kind.SCENE_APPLIED, "stop");

        assertEquals(NowPlaying.State.PAUSED, paused.state());
        assertEquals(T2, paused.since());
        assertEquals(NowPlaying.State.STOPPED, stopped.state());
        assertEquals(T3, stopped.since());
    }

    @DisplayName("A resume after a pause is Playing again and resets since")
    @Test
    public void resumeResetsSince() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");
        NowPlaying paused = apply(playing, PlexEvent.PAUSE, "Cold Open", T2, PlexOutcome.Kind.SCENE_APPLIED, "stop");

        NowPlaying resumed = apply(paused, PlexEvent.RESUME, "Cold Open", T3, PlexOutcome.Kind.SCENE_APPLIED,
                "start");

        assertEquals(NowPlaying.State.PLAYING, resumed.state());
        assertEquals(T3, resumed.since());
    }

    @DisplayName("A scrobble while Playing leaves the state, since and title alone")
    @Test
    public void scrobbleLeavesStateAlone() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");

        NowPlaying after = apply(playing, PlexEvent.SCROBBLE, "Another Title", T3, PlexOutcome.Kind.IGNORED_RULE_OFF,
                null);

        assertEquals(playing, after);
    }

    @DisplayName("A scrobble that applies a scene records the scene but still leaves the state alone")
    @Test
    public void scrobbleRecordsSceneAppliedOnly() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");

        NowPlaying after = apply(playing, PlexEvent.SCROBBLE, "Cold Open", T3, PlexOutcome.Kind.SCENE_APPLIED, "stop");

        assertEquals(NowPlaying.State.PLAYING, after.state());
        assertEquals(T1, after.since());
        assertEquals("stop", after.sceneId());
    }

    @DisplayName("A scrobble with nothing played yet stays Idle")
    @Test
    public void scrobbleWhileIdleStaysIdle() {
        NowPlaying after = apply(NowPlaying.idle(), PlexEvent.SCROBBLE, "Cold Open", T1,
                PlexOutcome.Kind.IGNORED_RULE_OFF, null);

        assertEquals(NowPlaying.State.IDLE, after.state());
    }

    @DisplayName("An event whose outcome is not a scene keeps the last scene that was applied")
    @Test
    public void sceneIdIsTheLastAppliedScene() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");

        NowPlaying paused = apply(playing, PlexEvent.PAUSE, "Cold Open", T2, PlexOutcome.Kind.IGNORED_RULE_OFF, null);
        NowPlaying failed = apply(paused, PlexEvent.RESUME, "Cold Open", T3, PlexOutcome.Kind.FAILED, null);

        assertEquals("start", paused.sceneId());
        assertEquals("start", failed.sceneId());
    }

    @DisplayName("A second play of the same title keeps since; a different title restarts it")
    @Test
    public void sinceRestartsOnlyForANewTitleOrState() {
        NowPlaying first = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1, PlexOutcome.Kind.SCENE_APPLIED,
                "start");

        NowPlaying same = apply(first, PlexEvent.PLAY, "Cold Open", T2, PlexOutcome.Kind.SCENE_APPLIED, "start");
        NowPlaying next = apply(same, PlexEvent.PLAY, "Second Episode", T3, PlexOutcome.Kind.SCENE_APPLIED, "start");

        assertEquals(T1, same.since());
        assertEquals(T3, next.since());
        assertEquals("Second Episode", next.title());
    }

    @DisplayName("An unsupported event, or none, changes nothing")
    @Test
    public void unsupportedChangesNothing() {
        NowPlaying playing = apply(NowPlaying.idle(), PlexEvent.PLAY, "Cold Open", T1,
                PlexOutcome.Kind.SCENE_APPLIED, "start");

        assertSame(playing, playing.apply(event("media.rate", "x", T2, PlexOutcome.Kind.IGNORED_UNSUPPORTED, null),
                null));
        assertSame(playing, playing.apply(event("media.play", "x", T2, PlexOutcome.Kind.IGNORED_UNSUPPORTED, null),
                PlexEvent.PLAY));
    }

    @DisplayName("A recognised event with an unknown media type changes nothing")
    @Test
    public void unknownTypeChangesNothing() {
        NowPlaying idle = NowPlaying.idle();

        assertSame(idle, idle.apply(event("media.play", "x", T1, PlexOutcome.Kind.IGNORED_UNKNOWN_TYPE, null),
                PlexEvent.PLAY));
    }

    @DisplayName("The state serialises as its label")
    @Test
    public void stateSerialisesAsLabel() {
        assertEquals("\"Playing\"", new JsonMapper().writeValueAsString(NowPlaying.State.PLAYING));
        assertEquals("\"Paused\"", new JsonMapper().writeValueAsString(NowPlaying.State.PAUSED));
        assertEquals("\"Stopped\"", new JsonMapper().writeValueAsString(NowPlaying.State.STOPPED));
        assertEquals("\"Idle\"", new JsonMapper().writeValueAsString(NowPlaying.State.IDLE));
    }
}
