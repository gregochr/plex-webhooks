package gregochr.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** A clock pinned to 15 March 2024, a date the suite cannot be running on, so ignoring the clock fails a test. */
public final class TestClocks {
    public static final Instant INSTANT = Instant.parse("2024-03-15T12:00:00Z");
    public static final Clock FIXED = Clock.fixed(INSTANT, ZoneOffset.UTC);

    private TestClocks() {
    }
}
