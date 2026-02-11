package gregochr.commands;

import java.util.Arrays;
import java.util.Optional;

public enum PlexMediaType {
    MOVIE("movie"),
    EPISODE("episode"),
    CLIP("clip"),
    TRACK("track"),
    PHOTO("photo");

    private final String type;

    PlexMediaType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static Optional<PlexMediaType> fromType(String type) {
        return Arrays.stream(values())
                .filter(t -> t.type.equals(type))
                .findFirst();
    }
}
