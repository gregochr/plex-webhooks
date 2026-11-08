package gregochr.services.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SQL Errors Tests")
public class SqlErrorsTest {

    @DisplayName("SQL state 42P01 is a missing table, directly or anywhere in the cause chain")
    @Test
    public void missingTableDirectlyAndNested() {
        SQLException missing = new SQLException("relation \"x\" does not exist", "42P01");

        assertTrue(SqlErrors.isMissingTable(missing));
        assertTrue(SqlErrors.isMissingTable(new BadSqlGrammarException("insert", "insert", missing)));
        assertTrue(SqlErrors.isMissingTable(new IllegalStateException("wrapped",
                new RuntimeException("again", missing))));
    }

    @DisplayName("Any other SQL state, a failure with no SQL cause, and null are not a missing table")
    @Test
    public void otherFailuresAreNot() {
        assertFalse(SqlErrors.isMissingTable(new SQLException("syntax", "42601")));
        assertFalse(SqlErrors.isMissingTable(new SQLException("no state")));
        assertFalse(SqlErrors.isMissingTable(new IllegalStateException("plain")));
        assertFalse(SqlErrors.isMissingTable(null));
    }

    @DisplayName("A cause chain that loops back on itself ends instead of hanging")
    @Test
    public void cyclicCauseChainTerminates() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertFalse(SqlErrors.isMissingTable(a));
    }
}
