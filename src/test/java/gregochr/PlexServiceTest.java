package gregochr;

import gregochr.services.plex.PlexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlexServiceTest {

    @DisplayName("Stop playback test")
    @Test
    public void plexEventTest() {
        final String payload = "{"
                + "\"event\": \"media.resume\","
                +        "\"user\": true,"
                +        "\"owner\": true"
                + "}";
        //PlexService controller = new PlexService();
        //controller.plexEvent(payload);

        //assert(1 == 2);
    }
}