package gregochr.services.config;

/**
 * One difference between the settings file and the database copy. {@code oldValue} is null for a key first seen
 * (and for a key that is no longer allowed in the database: its old value is never copied), {@code newValue} is null
 * for a key removed.
 *
 * @param source one of {@link #INITIAL}, {@link #DASHBOARD} or {@link #SYNC}
 */
public record SettingChange(String key, String oldValue, String newValue, String source) {
    /** The first reconcile into an empty table. */
    public static final String INITIAL = "initial";
    /** The key was in a dashboard save since the last reconcile. */
    public static final String DASHBOARD = "dashboard";
    /** Any other difference: a hand edit of the file, or a save made while the database was down. */
    public static final String SYNC = "sync";
}
