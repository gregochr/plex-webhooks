package gregochr.clients;

import gregochr.clients.dto.LocationResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls the live postcodes.io API. Excluded from the build gate; run with
 * {@code ./mvnw test -Dsurefire.excludedGroups= -Dgroups=real-api}.
 */
@Tag("real-api")
public class LocationClientRealApiTest {
    private static final double TOLERANCE = 0.01;

    private static void assertNear(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < TOLERANCE,
                "expected within " + TOLERANCE + " of " + expected + " but was " + actual);
    }

    @DisplayName("Live: DH1 4HU resolves to roughly Durham")
    @Test
    public void durhamPostCode() {
        LocationResponseDto response = new LocationClient(RestClient.create(), "DH1 4HU").getLatitudeAndLogitude();

        assertEquals(200, response.status());
        assertNotNull(response.locationResultDto());
        assertNear(54.777797, response.locationResultDto().latitude());
        assertNear(-1.600031, response.locationResultDto().longitude());
    }

    @DisplayName("Live: lowercase post code with extra spaces resolves to the same place")
    @Test
    public void messyPostCode() {
        LocationResponseDto response = new LocationClient(RestClient.create(), "d h 1 4 h u").getLatitudeAndLogitude();

        assertEquals(200, response.status());
        assertNear(54.777797, response.locationResultDto().latitude());
        assertNear(-1.600031, response.locationResultDto().longitude());
    }

    @DisplayName("Live: SW1A 1AA resolves to roughly central London")
    @Test
    public void londonPostCode() {
        LocationResponseDto response = new LocationClient(RestClient.create(), "SW1A 1AA").getLatitudeAndLogitude();

        assertEquals(200, response.status());
        assertNear(51.50101, response.locationResultDto().latitude());
        assertNear(-0.141563, response.locationResultDto().longitude());
    }
}
