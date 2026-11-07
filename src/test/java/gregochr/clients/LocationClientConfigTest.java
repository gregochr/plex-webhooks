package gregochr.clients;

import gregochr.RestClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A bare Spring context, not Spring Boot: this only checks that the ${postcode} property is injected into the bean.
@SpringJUnitConfig({LocationClient.class, RestClientConfig.class})
@TestPropertySource(properties = "postcode=DH1 4HU")
public class LocationClientConfigTest {
    @Autowired
    LocationClient locationClient;

    @DisplayName("The postcode property is injected into the LocationClient")
    @Test
    public void getPostCodeFromConfig() {
        assertEquals("DH1 4HU", locationClient.getPostCode());
    }
}
