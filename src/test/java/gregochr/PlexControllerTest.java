package gregochr;

import gregochr.controllers.PlexController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlexControllerTest {

    @DisplayName("Stop playback test")
    @Test
    public void plexEventTest() {
        final String payload = "{"
                + "\"event\": \"media.resume\","
                +        "\"user\": true,"
                +        "\"owner\": true"
                + "}";
        PlexController controller = new PlexController();
        controller.plexEvent(payload);

        assert(1 == 2);
    }
}