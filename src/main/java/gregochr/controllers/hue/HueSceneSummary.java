package gregochr.controllers.hue;

/**
 * A Hue scene as a dashboard shows it.
 *
 * @param id        the scene's UUID
 * @param name      the scene's name
 * @param groupId   the UUID of the room or zone the scene belongs to; null if the bridge gave none
 * @param groupName that room's or zone's name; null if it could not be found
 * @param groupType {@code room} or {@code zone} (the scene's {@code group.rtype})
 * @param level     the brightness the scene sets, 0 to 100, averaged over its lights; null when the scene carries no
 *                  brightness data. It is the level the scene asks for, not a reading from the lights.
 */
public record HueSceneSummary(String id, String name, String groupId, String groupName, String groupType,
        Integer level) {
}
