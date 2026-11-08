package gregochr.services.events;

import gregochr.commands.PlexOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Playback History Tests")
public class PlaybackHistoryTest {

    static PlaybackEvent event(long id) {
        return new PlaybackEvent(id, "2024-03-15T12:00:00Z", "media.play", "movie", "Heat", null, null, null, null,
                null, PlexOutcome.Kind.IGNORED_RULE_OFF, null, "plex", "{}",
                new PlaybackEvent.Command("media.play", "Heat", 0));
    }

    private static List<Long> ids(List<PlaybackEvent> events) {
        return events.stream().map(PlaybackEvent::id).toList();
    }

    @DisplayName("A new history is empty")
    @Test
    public void startsEmpty() {
        assertEquals(List.of(), new PlaybackHistory().newestFirst(100));
    }

    @DisplayName("Events come back newest first")
    @Test
    public void newestFirst() {
        PlaybackHistory history = new PlaybackHistory();
        history.add(event(1));
        history.add(event(2));
        history.add(event(3));

        assertEquals(List.of(3L, 2L, 1L), ids(history.newestFirst(100)));
    }

    @DisplayName("The limit returns only that many of the newest events")
    @Test
    public void limitKeepsNewest() {
        PlaybackHistory history = new PlaybackHistory();
        IntStream.rangeClosed(1, 5).forEach(i -> history.add(event(i)));

        assertEquals(List.of(5L, 4L), ids(history.newestFirst(2)));
        assertEquals(List.of(5L), ids(history.newestFirst(1)));
    }

    @DisplayName("At exactly 100 events nothing has been dropped")
    @Test
    public void capacityBoundaryKeepsEverything() {
        PlaybackHistory history = new PlaybackHistory();
        IntStream.rangeClosed(1, 100).forEach(i -> history.add(event(i)));

        List<PlaybackEvent> all = history.newestFirst(1000);
        assertEquals(100, all.size());
        assertEquals(100L, all.get(0).id());
        assertEquals(1L, all.get(99).id());
    }

    @DisplayName("The 101st event pushes the oldest out")
    @Test
    public void oneOverCapacityDropsOldest() {
        PlaybackHistory history = new PlaybackHistory();
        IntStream.rangeClosed(1, 101).forEach(i -> history.add(event(i)));

        List<PlaybackEvent> all = history.newestFirst(1000);
        assertEquals(100, all.size());
        assertEquals(101L, all.get(0).id());
        assertEquals(2L, all.get(99).id());
    }

    @DisplayName("The capacity is 100")
    @Test
    public void capacityIsOneHundred() {
        assertEquals(100, PlaybackHistory.CAPACITY);
    }

    @DisplayName("Seeding an empty history loads the stored events, newest first")
    @Test
    public void seedLoadsStored() {
        PlaybackHistory history = new PlaybackHistory();

        history.seed(List.of(event(3), event(2), event(1)));

        assertEquals(List.of(3L, 2L, 1L), ids(history.newestFirst(100)));
    }

    @DisplayName("Seeding merges with events recorded since boot by id, and an event in memory wins over a stored one")
    @Test
    public void seedMergesById() {
        PlaybackHistory history = new PlaybackHistory();
        PlaybackEvent inMemory = new PlaybackEvent(5, "2024-03-15T12:00:00Z", "media.stop", "movie", "Heat", null,
                null, null, null, null, PlexOutcome.Kind.SCENE_APPLIED, "scene", "plex", "{}",
                new PlaybackEvent.Command("media.stop", "Heat", 0));
        history.add(event(6));
        history.add(inMemory);

        history.seed(List.of(event(5), event(4), event(3)));

        List<PlaybackEvent> all = history.newestFirst(100);
        assertEquals(List.of(6L, 5L, 4L, 3L), ids(all));
        assertEquals(inMemory, all.get(1));
    }

    @DisplayName("Seeding keeps the newest 100 by id, however the rows arrive")
    @Test
    public void seedKeepsNewestHundred() {
        PlaybackHistory history = new PlaybackHistory();
        history.add(event(150));

        history.seed(IntStream.rangeClosed(1, 120).mapToObj(i -> event(i)).toList());

        List<PlaybackEvent> all = history.newestFirst(1000);
        assertEquals(100, all.size());
        assertEquals(150L, all.get(0).id());
        assertEquals(22L, all.get(99).id());
    }

    @DisplayName("Seeding exactly 100 events drops none")
    @Test
    public void seedAtCapacityDropsNone() {
        PlaybackHistory history = new PlaybackHistory();

        history.seed(IntStream.rangeClosed(1, 100).mapToObj(i -> event(i)).toList());

        List<PlaybackEvent> all = history.newestFirst(1000);
        assertEquals(100, all.size());
        assertEquals(1L, all.get(99).id());
    }

    @DisplayName("After seeding, a new event is appended as the newest and the oldest is pushed out at 100")
    @Test
    public void addAfterSeed() {
        PlaybackHistory history = new PlaybackHistory();
        history.seed(IntStream.rangeClosed(1, 100).mapToObj(i -> event(i)).toList());

        history.add(event(101));

        List<PlaybackEvent> all = history.newestFirst(1000);
        assertEquals(101L, all.get(0).id());
        assertEquals(2L, all.get(99).id());
    }

    @DisplayName("Seeding with nothing stored leaves the history as it was")
    @Test
    public void seedEmptyKeepsEvents() {
        PlaybackHistory history = new PlaybackHistory();
        history.add(event(1));

        history.seed(List.of());

        assertEquals(List.of(1L), ids(history.newestFirst(100)));
    }
}
