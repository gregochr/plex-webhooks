package gregochr.services.events;

/**
 * Something that has work to do once the database is migrated and ready, run by {@link PlaybackDatabaseLifecycle}.
 * A listener is retried every 30 s until it has succeeded once, so {@link #onDatabaseReady()} must be idempotent
 * and must not do long work: it runs on the migration thread. If it throws, only this listener is retried; it
 * neither blocks the other listeners nor undoes them.
 */
public interface DatabaseReadyListener {

    /** A short name for logs. */
    String name();

    /** Runs when the database is ready; throw to be retried in 30 s. */
    void onDatabaseReady();

    /**
     * Gives the listener the way to ask for the schema to be recreated, which it calls when it finds a table
     * missing (SQL state 42P01). The default does nothing, for a listener that never sees that failure.
     */
    default void onSchemaMissing(Runnable restartMigration) {
    }

    /** Stops anything the listener scheduled. Called once when the application shuts down. */
    default void stop() {
    }
}
