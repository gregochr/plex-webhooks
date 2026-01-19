package gregochr;

import org.springframework.beans.factory.annotation.Autowired;

public class LocationController {
    @Autowired
    private PlexApplicationProperties plexApplicationProperties;

    public LocationController() {

    }

    public String toString () {
        return plexApplicationProperties.getPostCode();
    }
}
