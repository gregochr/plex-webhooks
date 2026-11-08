package gregochr.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Throw-away settings files for tests that point {@code cinelight.config-file} at one. */
public final class SettingsFiles {
    /** A small file with the keys a dashboard save may write, and one credential with a fake value. */
    public static final List<String> TYPICAL = List.of("postcode=DH1 4HU", "sse.heartbeat-interval=10000",
            "hue.start-scene=start-scene", "plex.movie-play-enabled=true", "hue.application-key=FAKE-KEY-DO-NOT-STORE");

    private SettingsFiles() {
    }

    /** Creates a settings file holding the lines, deleted when the JVM exits. */
    public static Path create(List<String> lines) {
        try {
            Path dir = Files.createTempDirectory("cinelight-settings-test");
            dir.toFile().deleteOnExit();
            Path file = dir.resolve("application.properties");
            Files.write(file, lines);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
