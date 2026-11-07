package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BooleanProperties Tests")
public class BooleanPropertiesTest {

    @DisplayName("true, on, yes and 1 are true in any case")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"true", "TRUE", "True", "on", "ON", "On", "yes", "YES", "Yes", "1"})
    public void trueValues(String value) {
        assertTrue(BooleanProperties.parse(value));
    }

    @DisplayName("Surrounding whitespace is trimmed")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {" true", "true ", "\tyes\n", "  1  ", " on "})
    public void paddedTrueValues(String value) {
        assertTrue(BooleanProperties.parse(value));
    }

    @DisplayName("false, off, no, 0, empty, blank, near-misses and garbage are false")
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"false", "FALSE", "off", "no", "0", "", " ", "t", "y", "truee", "ye", "11", "2", "-1",
            "maybe", "tru e"})
    public void falseValues(String value) {
        assertFalse(BooleanProperties.parse(value));
    }

    @DisplayName("null is false")
    @Test
    public void nullIsFalse() {
        assertFalse(BooleanProperties.parse(null));
    }
}
