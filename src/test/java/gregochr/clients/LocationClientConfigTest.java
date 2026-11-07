package gregochr.clients;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LocationClientConfigTest {
    @Autowired
    LocationClient locationClient;

    @DisplayName("Get post code from config file is DH1 4HU")
    @Test
    public void getPostCodeFromConfig() {
        assertEquals("DH1 4HU", locationClient.getPostCode());
    }
}
