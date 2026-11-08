package gregochr.services.events;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Reads what a database failure means, wherever in the cause chain Spring or the driver put the detail. */
public final class SqlErrors {
    /** PostgreSQL's SQL state for "undefined table". */
    static final String MISSING_TABLE = "42P01";

    private SqlErrors() {
    }

    /** True when the failure, or any cause of it, is SQL state 42P01: the table is gone (a rebuilt database, say). */
    public static boolean isMissingTable(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        while (cause != null && seen.add(cause)) {
            if (cause instanceof SQLException sql && MISSING_TABLE.equals(sql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
