package gregochr.commands;

/**
 * What happened to a Plex event: whether it changed the lights and, if so, which scene it activated.
 *
 * @param kind    the outcome category
 * @param sceneId the Hue scene that was activated; non-null only for {@link Kind#SCENE_APPLIED}
 */
public record PlexOutcome(Kind kind, String sceneId) {

    /** The ways a Plex event can end. */
    public enum Kind {
        /** The event is not one CineLight acts on (unrecognised event or media type). */
        IGNORED_UNSUPPORTED,
        /** Hue integration is switched off. */
        IGNORED_HUE_DISABLED,
        /** The command named a media type CineLight does not know (for example a bad Debug {@code type}). */
        IGNORED_UNKNOWN_TYPE,
        /** The rule for this event and media type is switched off. */
        IGNORED_RULE_OFF,
        /** The scene was activated. */
        SCENE_APPLIED,
        /** The bridge did not activate the scene, or the Hue path threw. */
        FAILED
    }

    public static PlexOutcome of(Kind kind) {
        return new PlexOutcome(kind, null);
    }

    public static PlexOutcome applied(String sceneId) {
        return new PlexOutcome(Kind.SCENE_APPLIED, sceneId);
    }

    /** True only when a scene was activated; this is what the legacy boolean {@code execute} reports. */
    public boolean isApplied() {
        return kind == Kind.SCENE_APPLIED;
    }
}
